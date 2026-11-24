# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import _sdk_version
_sdk_version.Z.c()

import re
from dataclasses import dataclass
from enum import Enum
from typing import List, Tuple, Optional

from org.telegram.tgnet import TLRPC

__all__ = ["parse_markdown"]


class TLEntityType(Enum):
    CODE = 'code'
    PRE = 'pre'
    STRIKETHROUGH = 'strikethrough'
    TEXT_LINK = 'text_link'
    BOLD = 'bold'
    ITALIC = 'italic'
    UNDERLINE = 'underline'
    SPOILER = 'spoiler'
    CUSTOM_EMOJI = 'custom_emoji'


@dataclass
class RawEntity:
    """
    An object representing the TLRPC message entity. Contains offset, length, and
    other optional parameters. Must be converted to a TLRPC object using
    ``RawEntity.to_tlrpc_object()`` before sending.
    """
    type: TLEntityType
    offset: int
    length: int
    language: Optional[str] = None
    url: Optional[str] = None
    document_id: Optional[int] = None

    def to_tlrpc_object(self):
        entity = TLRPC_ENTITIES_MAP[self.type]()
        entity.offset = self.offset
        entity.length = self.length
        if self.language is not None: entity.language = self.language
        if self.url is not None: entity.url = self.url
        if self.document_id is not None: entity.document_id = self.document_id
        return entity


@dataclass
class ParsedMessage:
    """
    An object containing the final message text and a list of RawEntity objects.
    """
    text: str
    entities: Tuple[RawEntity, ...]


def count_chars_until(string: str, stop_chars: str, start_index: int = 0) -> int:
    """
    Returns the number of characters in 'string' (starting at 'start_index')
    until any character from 'stop_chars' is encountered.
    """
    for index, char in enumerate(string[start_index:], start=start_index):
        if char in stop_chars:
            return index - start_index
    return len(string) - start_index


def to_utf16_len(s: str) -> int:
    return len(s.encode('utf-16-le')) // 2

def parse_markdown(markdown: str) -> ParsedMessage:
    """
    :raises SyntaxError: when encountered incorrect markdown syntax
    """
    markdown = markdown.replace("\r\n", "\n").strip()
    message = ""
    message_len = 0
    python_entities: List[RawEntity] = []
    offset = 0
    stack = []

    stop_chars = '*_~`[]|!\\'

    def flush_text_to_message(text_to_flush: str):
        nonlocal message, message_len
        message += text_to_flush
        message_len += len(text_to_flush)

    while offset < len(markdown):
        n = count_chars_until(markdown, stop_chars, offset)
        plain_text_segment = markdown[offset: offset + n]
        offset += n

        flush_text_to_message(plain_text_segment)

        if offset == len(markdown):
            break

        marker_char = markdown[offset]
        offset += 1
        next_char_in_markdown = markdown[offset] if offset < len(markdown) else ''

        if marker_char == '\\':
            if next_char_in_markdown:
                flush_text_to_message(next_char_in_markdown)
                offset += 1
            else:
                flush_text_to_message(marker_char)
            continue

        if marker_char == '[':
            _py_link_text_start_in_message = message_len
            _link_text = ""
            _markdown_offset_at_link_text_start = offset

            while offset < len(markdown) and markdown[offset] != ']':
                if markdown[offset] == '\\' and offset + 1 < len(markdown):
                    _link_text += markdown[offset + 1]
                    offset += 2
                else:
                    _link_text += markdown[offset]
                    offset += 1

            if offset >= len(markdown) or markdown[offset] != ']' or \
                    offset + 1 >= len(markdown) or markdown[offset + 1] != '(':
                flush_text_to_message(marker_char)
                offset = _markdown_offset_at_link_text_start
                continue

            offset += 2
            _url_or_doc_id = ""
            _markdown_offset_at_url_start = offset
            while offset < len(markdown) and markdown[offset] != ')':
                if markdown[offset] == '\\' and offset + 1 < len(markdown):
                    _url_or_doc_id += markdown[offset + 1]
                    offset += 2
                else:
                    _url_or_doc_id += markdown[offset]
                    offset += 1

            if offset >= len(markdown) or markdown[offset] != ')':
                flush_text_to_message(marker_char + _link_text + "]" + "(")
                offset = _markdown_offset_at_url_start
                continue

            offset += 1

            flush_text_to_message(_link_text)
            _py_link_text_len = len(_link_text)

            if _url_or_doc_id.isdigit() and _py_link_text_len > 0:
                doc_id = int(_url_or_doc_id)
                python_entities.append(RawEntity(type=TLEntityType.CUSTOM_EMOJI,
                                                 offset=_py_link_text_start_in_message,
                                                 length=_py_link_text_len,
                                                 document_id=doc_id))
            elif _py_link_text_len > 0:
                python_entities.append(RawEntity(type=TLEntityType.TEXT_LINK,
                                                 offset=_py_link_text_start_in_message,
                                                 length=_py_link_text_len,
                                                 url=_url_or_doc_id))
            continue

        if marker_char == '`':
            start_offset = offset - 1
            fence_len = 1
            while offset < len(markdown) and markdown[offset] == '`':
                fence_len += 1
                offset += 1

            token = '`' * fence_len
            language = None

            if fence_len >= 3:
                lang_match = re.match(r'([^\s\n]+)', markdown[offset:])
                if lang_match:
                    language = lang_match.group(1)
                    offset += len(language)
                if offset < len(markdown) and markdown[offset] == '\n':
                    offset += 1

            code_content_start_offset_in_markdown = offset
            pos_close_search_start = offset
            pos_close = -1

            while True:
                _pos_close_candidate = markdown.find(token, pos_close_search_start)
                if _pos_close_candidate == -1:
                    raise ValueError(f"Unclosed {token} opened near offset {start_offset}!")

                pos_close = _pos_close_candidate
                break

            code_piece = markdown[code_content_start_offset_in_markdown: pos_close]

            _py_code_start_in_message = message_len
            flush_text_to_message(code_piece)

            trimmed_py_len = len(code_piece)
            for i in reversed(range(len(code_piece))):
                if code_piece[i] not in (' ', '\r', '\n'):
                    break
                trimmed_py_len -= 1

            if trimmed_py_len > 0:
                entity_type = TLEntityType.PRE if fence_len >= 3 else TLEntityType.CODE
                python_entities.append(RawEntity(type=entity_type,
                                                 offset=_py_code_start_in_message,
                                                 length=trimmed_py_len,
                                                 language=language))

            offset = pos_close + len(token)
            continue

        current_marker_string = marker_char
        if marker_char == '_' and next_char_in_markdown == '_':
            current_marker_string = '__'
            offset += 1
        elif marker_char == '|' and next_char_in_markdown == '|':
            current_marker_string = '||'
            offset += 1

        stackable_entity_map = {
            '*': TLEntityType.BOLD,
            '_': TLEntityType.ITALIC,
            '__': TLEntityType.UNDERLINE,
            '~': TLEntityType.STRIKETHROUGH,
            '||': TLEntityType.SPOILER
        }

        if current_marker_string in stackable_entity_map:
            if current_marker_string == '_' and re.search(r'@\w+$', message):
                flush_text_to_message(current_marker_string)
                continue

            if stack and stack[-1][0] == current_marker_string:
                _, _py_entity_start_in_message = stack.pop()
                entity_type = stackable_entity_map[current_marker_string]

                py_content_actual = message[_py_entity_start_in_message:message_len]
                py_length_real = len(py_content_actual)
                for i in reversed(range(len(py_content_actual))):
                    if py_content_actual[i] not in (' ', '\r', '\n'):
                        break
                    py_length_real -= 1

                if py_length_real > 0:
                    python_entities.append(RawEntity(type=entity_type,
                                                     offset=_py_entity_start_in_message,
                                                     length=py_length_real))
            else:
                stack.append((current_marker_string, message_len))
        else:
            flush_text_to_message(current_marker_string)

    if stack:
        open_markers_info = ", ".join(
            f"'{marker_str}' at message offset {start_pos}" for marker_str, start_pos in stack)
        raise ValueError(f"Found unclosed markdown elements: {open_markers_info}")

    final_entities: List[RawEntity] = []

    python_entities.sort(key=lambda x: x.offset)

    if not python_entities:
        return ParsedMessage(text=message, entities=tuple())

    current_py_offset = 0
    current_utf16_offset = 0

    for py_entity in python_entities:
        segment_before = message[current_py_offset : py_entity.offset]
        utf16_step = to_utf16_len(segment_before)

        start_utf16 = current_utf16_offset + utf16_step

        entity_text = message[py_entity.offset : py_entity.offset + py_entity.length]
        length_utf16 = to_utf16_len(entity_text)

        final_entities.append(RawEntity(
            type=py_entity.type,
            offset=start_utf16,
            length=length_utf16,
            language=py_entity.language,
            url=py_entity.url,
            document_id=py_entity.document_id
        ))

        current_utf16_offset = start_utf16 + length_utf16
        current_py_offset = py_entity.offset + py_entity.length

    return ParsedMessage(text=message, entities=tuple(final_entities))


TLRPC_ENTITIES_MAP = {
    TLEntityType.CODE: TLRPC.TL_messageEntityCode,
    TLEntityType.PRE: TLRPC.TL_messageEntityPre,
    TLEntityType.STRIKETHROUGH: TLRPC.TL_messageEntityStrike,
    TLEntityType.TEXT_LINK: TLRPC.TL_messageEntityTextUrl,
    TLEntityType.BOLD: TLRPC.TL_messageEntityBold,
    TLEntityType.ITALIC: TLRPC.TL_messageEntityItalic,
    TLEntityType.UNDERLINE: TLRPC.TL_messageEntityUnderline,
    TLEntityType.SPOILER: TLRPC.TL_messageEntitySpoiler,
    TLEntityType.CUSTOM_EMOJI: TLRPC.TL_messageEntityCustomEmoji,
}
