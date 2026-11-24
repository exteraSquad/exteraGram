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

import html
import re
from dataclasses import dataclass
from enum import Enum
from html.parser import HTMLParser
from struct import unpack
from typing import Optional, List, Union, Tuple, Dict, Any

from org.telegram.tgnet import TLRPC


##############################   Markdown & HTML parsers   ##############################

def add_surrogates(text: str) -> str:
    return re.compile(r"[\U00010000-\U0010FFFF]").sub(
        lambda match:
        "".join(chr(i) for i in unpack("<HH", match.group().encode("utf-16le"))),
        text
    )


def remove_surrogates(text: str) -> str:
    return text.encode("utf-16", "surrogatepass").decode("utf-16")


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
    BLOCKQUOTE = 'blockquote'

    @classmethod
    def from_(cls, entity):
        return (
            cls.CODE
            if isinstance(entity, TLRPC.TL_messageEntityCode)
            else cls.PRE
            if isinstance(entity, TLRPC.TL_messageEntityPre)
            else cls.STRIKETHROUGH
            if isinstance(entity, TLRPC.TL_messageEntityStrike)
            else cls.TEXT_LINK
            if isinstance(entity, TLRPC.TL_messageEntityTextUrl)
            else cls.BOLD
            if isinstance(entity, TLRPC.TL_messageEntityBold)
            else cls.ITALIC
            if isinstance(entity, TLRPC.TL_messageEntityItalic)
            else cls.UNDERLINE
            if isinstance(entity, TLRPC.TL_messageEntityUnderline)
            else cls.SPOILER
            if isinstance(entity, TLRPC.TL_messageEntitySpoiler)
            else cls.CUSTOM_EMOJI
            if isinstance(entity, TLRPC.TL_messageEntityCustomEmoji)
            else cls.BLOCKQUOTE
            if isinstance(entity, TLRPC.TL_messageEntityBlockquote)
            else None
        )


@dataclass
class RawEntity:
    @property
    def TLRPC_ENTITIES_MAP(self):
        return {
            TLEntityType.CODE: TLRPC.TL_messageEntityCode,
            TLEntityType.PRE: TLRPC.TL_messageEntityPre,
            TLEntityType.STRIKETHROUGH: TLRPC.TL_messageEntityStrike,
            TLEntityType.TEXT_LINK: TLRPC.TL_messageEntityTextUrl,
            TLEntityType.BOLD: TLRPC.TL_messageEntityBold,
            TLEntityType.ITALIC: TLRPC.TL_messageEntityItalic,
            TLEntityType.UNDERLINE: TLRPC.TL_messageEntityUnderline,
            TLEntityType.SPOILER: TLRPC.TL_messageEntitySpoiler,
            TLEntityType.CUSTOM_EMOJI: TLRPC.TL_messageEntityCustomEmoji,
            TLEntityType.BLOCKQUOTE: TLRPC.TL_messageEntityBlockquote
        }

    type: TLEntityType
    offset: int
    length: int
    language: Optional[str] = None
    url: Optional[str] = None
    document_id: Optional[int] = None
    collapsed: Optional[bool] = None

    def to_tlrpc_object(self):
        entity = self.TLRPC_ENTITIES_MAP[self.type]()
        entity.offset = self.offset
        entity.length = self.length

        if self.language is not None and self.type == TLEntityType.PRE:
            entity.language = self.language
        if self.url is not None and self.type == TLEntityType.TEXT_LINK:
            entity.url = self.url
        if self.document_id is not None and self.type == TLEntityType.CUSTOM_EMOJI:
            entity.document_id = self.document_id
        if self.collapsed is not None and self.type == TLEntityType.BLOCKQUOTE:
            entity.collapsed = self.collapsed

        return entity


class Parser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.text = ""
        self.entities = []
        self.tag_entities = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        extra = {}

        if tag in ["b", "strong"]:
            _type = TLEntityType.BOLD
        elif tag in ["i", "em"]:
            _type = TLEntityType.ITALIC
        elif tag == "u":
            _type = TLEntityType.UNDERLINE
        elif tag in ["s", "del", "strike"]:
            _type = TLEntityType.STRIKETHROUGH
        elif tag == "blockquote":
            _type = TLEntityType.BLOCKQUOTE
            extra["collapsed"] = "expandable" in attrs or "collapsed" in attrs
        elif tag == "code":
            _type = TLEntityType.CODE
        elif tag == "pre":
            _type = TLEntityType.PRE
            extra["language"] = attrs.get("language", attrs.get("lang", ""))
        elif tag in ["spoiler", "tg-spoiler"]:
            _type = TLEntityType.SPOILER
        elif tag == "a":
            url = attrs.get("href", "")
            _type = TLEntityType.TEXT_LINK
            extra["url"] = url
        elif tag == "emoji":
            _type = TLEntityType.CUSTOM_EMOJI
            custom_emoji_id = attrs.get("id", attrs.get("document_id", "0"))
            if not custom_emoji_id or not custom_emoji_id.isdigit():
                return
            extra["document_id"] = int(custom_emoji_id)
        else:
            return

        if tag not in self.tag_entities:
            self.tag_entities[tag] = []

        self.tag_entities[tag].append(
            RawEntity(
                type=_type,
                offset=len(self.text),
                length=0,
                **extra
            ))

    def handle_data(self, data):
        data = html.unescape(data)

        for entities in self.tag_entities.values():
            for entity in entities:
                entity.length += len(data)

        self.text += data

    def handle_endtag(self, tag):
        try:
            self.entities.append(self.tag_entities[tag].pop())
        except (KeyError, IndexError):
            pass
        else:
            if not self.tag_entities[tag]:
                self.tag_entities.pop(tag)


class HTML:
    @staticmethod
    def parse(text: str) -> dict:
        text = re.sub(r"^\s*(<[\w<>=\s\"]*>)\s*", r"\1", text)
        text = re.sub(r"\s*(</[\w</>]*>)\s*$", r"\1", text)

        parser = Parser()
        parser.feed(add_surrogates(text))
        parser.close()

        if parser.tag_entities:
            unclosed_tags = []

            for tag, entities in parser.tag_entities.items():
                unclosed_tags.append(f"<{tag}> (x{len(entities)})")

        entities = []

        for entity in parser.entities:
            if entity.length == 0:
                continue

            entities.append(entity)

        entities = list(filter(lambda x: x.length > 0, entities))

        return {
            "message": remove_surrogates(parser.text),
            "entities": sorted(entities, key=lambda e: e.offset)
        }

    @staticmethod
    def unparse(text: str, entities: list) -> str:
        def parse_one(e):
            """
            Parses a single entity and returns (start_tag, start), (end_tag, end)
            """
            entity_type = TLEntityType.from_(e)
            start = e.offset
            end = start + e.length

            if entity_type in (
                    TLEntityType.BOLD,
                    TLEntityType.ITALIC,
                    TLEntityType.UNDERLINE,
                    TLEntityType.STRIKETHROUGH,
            ):
                name = entity_type.name[0].lower()
                start_tag = f"<{name}>"
                end_tag = f"</{name}>"
            elif entity_type == TLEntityType.PRE:
                name = entity_type.name.lower()
                language = getattr(e, "language", "") or ""
                start_tag = f'<{name} language="{language}">' if language else f"<{name}>"
                end_tag = f"</{name}>"
            elif entity_type == TLEntityType.BLOCKQUOTE:
                name = entity_type.name.lower()
                expandable = getattr(e, "expandable", False)
                start_tag = f'<{name}{" expandable" if expandable else ""}>'
                end_tag = f"</{name}>"
            elif entity_type in (
                    TLEntityType.CODE,
                    TLEntityType.SPOILER,
            ):
                name = entity_type.name.lower()
                start_tag = f"<{name}>"
                end_tag = f"</{name}>"
            elif entity_type == TLEntityType.TEXT_LINK:
                url = e.url
                start_tag = f'<a href="{url}">'
                end_tag = "</a>"
            elif entity_type == TLEntityType.CUSTOM_EMOJI:
                custom_emoji_id = e.document_id
                start_tag = f'<emoji id={custom_emoji_id}>'
                end_tag = "</emoji>"
            else:
                return

            return (start_tag, start), (end_tag, end)

        def recursive(entity_i: int) -> int:
            """
            Takes the index of the entity to start parsing from, returns the number of parsed entities inside it.
            Uses entities_offsets as a stack, pushing (start_tag, start) first, then parsing nested entities,
            and finally pushing (end_tag, end) to the stack.
            No need to sort at the end.
            """
            this = parse_one(entities[entity_i])
            if this is None:
                return 1
            (start_tag, start), (end_tag, end) = this
            entities_offsets.append((start_tag, start))
            internal_i = entity_i + 1
            # while the next entity is inside the current one, keep parsing
            while internal_i < len(entities) and entities[internal_i].offset < end:
                internal_i += recursive(internal_i)
            entities_offsets.append((end_tag, end))
            return internal_i - entity_i

        text = add_surrogates(text)

        entities_offsets = []

        # probably useless because entities are already sorted by telegram
        entities.sort(key=lambda e: (e.offset, -e.length))

        # main loop for first-level entities
        i = 0
        while i < len(entities):
            i += recursive(i)

        if entities_offsets:
            last_offset = entities_offsets[-1][1]
            # no need to sort, but still add entities starting from the end
            for entity, offset in reversed(entities_offsets):
                text = text[:offset] + entity + html.escape(text[offset:last_offset]) + text[last_offset:]
                last_offset = offset

        return remove_surrogates(text)


BOLD_DELIM = "*"
ITALIC_DELIM = "_"
UNDERLINE_DELIM = "__"
STRIKE_DELIM = "~"
SPOILER_DELIM = "||"
CODE_DELIM = "`"
PRE_DELIM = "```"
BLOCKQUOTE_DELIM = ">"
BLOCKQUOTE_EXPANDABLE_DELIM = "**>"
BLOCKQUOTE_EXPANDABLE_END_DELIM = "||"

MARKDOWN_RE = re.compile(r"({d})|(!?)\[(.+?)]\((.+?)\)".format(
    d="|".join(
        ["".join(i) for i in [
            [rf"\{j}" for j in i]
            for i in [
                PRE_DELIM,
                CODE_DELIM,
                STRIKE_DELIM,
                UNDERLINE_DELIM,
                ITALIC_DELIM,
                BOLD_DELIM,
                SPOILER_DELIM
            ]
        ]]
    )))

OPENING_TAG = "<{}>"
CLOSING_TAG = "</{}>"
URL_MARKUP = '<a href="{}">{}</a>'
EMOJI_MARKUP = "<emoji id={}>{}</emoji>"
FIXED_WIDTH_DELIMS = [CODE_DELIM, PRE_DELIM]
MARKDOWN_ESCAPABLE_CHARS = "\\_*[]()~`|>!"
ESCAPED_MARKDOWN_RE = re.compile(
    r"\\([{}])".format(re.escape(MARKDOWN_ESCAPABLE_CHARS))
)


def replace_once(source: str, old: str, new: str, start: int):
    return source[:start] + source[start:].replace(old, new, 1)


class Markdown:
    @staticmethod
    def protect_escaped_chars(text: str) -> Tuple[str, Dict[str, str]]:
        escaped_chars: Dict[str, str] = {}

        def replace(match: re.Match) -> str:
            placeholder = f"\uE000{len(escaped_chars)}\uE001"
            escaped_chars[placeholder] = match.group(1)
            return placeholder

        return ESCAPED_MARKDOWN_RE.sub(replace, text), escaped_chars

    @staticmethod
    def restore_escaped_chars(text: str, escaped_chars: Dict[str, str]) -> str:
        for placeholder, value in escaped_chars.items():
            text = text.replace(placeholder, value)

        return text

    @staticmethod
    def escape_and_create_quotes(text: str, strict: bool):
        text_lines: List[Union[str, None]] = text.splitlines()

        # Indexes of Already escaped lines
        html_escaped_list: List[int] = []

        # Temporary Queue to hold lines to be quoted
        to_quote_list: List[Tuple[int, str]] = []

        def create_blockquote(expandable: bool = False) -> None:
            """
            Merges all lines in quote_queue into first line of queue
            Encloses that line in html quote
            Replaces rest of the lines with None placeholders to preserve indexes
            """
            if len(to_quote_list) == 0:
                return

            joined_lines = "\n".join([i[1] for i in to_quote_list])

            first_line_index, _ = to_quote_list[0]
            text_lines[first_line_index] = (
                f"<blockquote{' expandable' if expandable else ''}>{joined_lines}</blockquote>"
            )

            for line_to_remove in to_quote_list[1:]:
                text_lines[line_to_remove[0]] = None

            to_quote_list.clear()

        # Handle Expandable Quote
        inside_blockquote = False
        for index, line in enumerate(text_lines):
            if line.startswith(BLOCKQUOTE_EXPANDABLE_DELIM) and not inside_blockquote:
                delim_stripped_line = line[len(BLOCKQUOTE_EXPANDABLE_DELIM) + (
                    1 if line.startswith(f"{BLOCKQUOTE_EXPANDABLE_DELIM} ") else 0):]
                parsed_line = (
                    html.escape(delim_stripped_line) if strict else delim_stripped_line
                )

                to_quote_list.append((index, parsed_line))
                html_escaped_list.append(index)

                inside_blockquote = True
                continue

            elif line.endswith(BLOCKQUOTE_EXPANDABLE_END_DELIM) and inside_blockquote:
                if line.startswith(BLOCKQUOTE_DELIM):
                    line = line[len(BLOCKQUOTE_DELIM) + (1 if line.startswith(f"{BLOCKQUOTE_DELIM} ") else 0):]

                delim_stripped_line = line[:-len(BLOCKQUOTE_EXPANDABLE_END_DELIM)]

                parsed_line = (
                    html.escape(delim_stripped_line) if strict else delim_stripped_line
                )

                to_quote_list.append((index, parsed_line))
                html_escaped_list.append(index)

                inside_blockquote = False

                create_blockquote(expandable=True)

            if inside_blockquote:
                parsed_line = line[len(BLOCKQUOTE_DELIM) + (1 if line.startswith(f"{BLOCKQUOTE_DELIM} ") else 0):]
                parsed_line = html.escape(parsed_line) if strict else parsed_line
                to_quote_list.append((index, parsed_line))
                html_escaped_list.append(index)

        # Handle Single line/Continued Quote
        for index, line in enumerate(text_lines):
            if line is None:
                continue

            if line.startswith(BLOCKQUOTE_DELIM):
                delim_stripped_line = line[
                                      len(BLOCKQUOTE_DELIM) + (1 if line.startswith(f"{BLOCKQUOTE_DELIM} ") else 0):]
                parsed_line = (
                    html.escape(delim_stripped_line) if strict else delim_stripped_line
                )

                to_quote_list.append((index, parsed_line))
                html_escaped_list.append(index)

            elif len(to_quote_list) > 0:
                create_blockquote()
        else:
            create_blockquote()

        if strict:
            for idx, line in enumerate(text_lines):
                if idx not in html_escaped_list:
                    text_lines[idx] = html.escape(line)

        return "\n".join(
            [valid_line for valid_line in text_lines if valid_line is not None]
        )

    @classmethod
    def parse(cls, text: str, strict: bool = False):
        text, escaped_chars = cls.protect_escaped_chars(text)
        text = cls.escape_and_create_quotes(text, strict=strict)
        delims = set()
        is_fixed_width = False

        for i, match in enumerate(re.finditer(MARKDOWN_RE, text)):
            start, _ = match.span()
            delim, is_emoji, text_url, url = match.groups()
            full = match.group(0)

            if delim in FIXED_WIDTH_DELIMS:
                is_fixed_width = not is_fixed_width

            if is_fixed_width and delim not in FIXED_WIDTH_DELIMS:
                continue

            if not is_emoji and text_url:
                text = replace_once(text, full, URL_MARKUP.format(url, text_url), start)
                continue

            if is_emoji:
                emoji = text_url
                emoji_id = url.lstrip("tg://emoji?id=")
                text = replace_once(text, full, EMOJI_MARKUP.format(emoji_id, emoji), start)
                continue

            if delim == BOLD_DELIM:
                tag = "b"
            elif delim == ITALIC_DELIM:
                tag = "i"
            elif delim == UNDERLINE_DELIM:
                tag = "u"
            elif delim == STRIKE_DELIM:
                tag = "s"
            elif delim == CODE_DELIM:
                tag = "code"
            elif delim == PRE_DELIM:
                tag = "pre"
            elif delim == SPOILER_DELIM:
                tag = "spoiler"
            else:
                continue

            if delim not in delims:
                delims.add(delim)
                tag = OPENING_TAG.format(tag)
            else:
                delims.remove(delim)
                tag = CLOSING_TAG.format(tag)

            if delim == PRE_DELIM and delim in delims:
                delim_and_language = text[text.find(PRE_DELIM):].split("\n")[0]
                language = delim_and_language[len(PRE_DELIM):]
                text = replace_once(text, delim_and_language, f'<pre language="{language}">', start)
                continue

            text = replace_once(text, delim, tag, start)

        text = cls.restore_escaped_chars(text, escaped_chars)
        return HTML.parse(text)

    @staticmethod
    def unparse(text: str, entities: list):
        text = add_surrogates(text)
        entities_offsets = []

        for entity in entities:
            entity_type = TLEntityType.from_(entity)
            start = entity.offset
            end = start + entity.length

            if entity_type == TLEntityType.BOLD:
                start_tag = end_tag = BOLD_DELIM
            elif entity_type == TLEntityType.ITALIC:
                start_tag = end_tag = ITALIC_DELIM
            elif entity_type == TLEntityType.UNDERLINE:
                start_tag = end_tag = UNDERLINE_DELIM
            elif entity_type == TLEntityType.STRIKETHROUGH:
                start_tag = end_tag = STRIKE_DELIM
            elif entity_type == TLEntityType.CODE:
                start_tag = end_tag = CODE_DELIM
            elif entity_type == TLEntityType.PRE:
                language = getattr(entity, "language", "") or ""
                start_tag = f"{PRE_DELIM}{language}\n"
                end_tag = f"\n{PRE_DELIM}"
            elif entity_type == TLEntityType.BLOCKQUOTE:
                start_tag = BLOCKQUOTE_DELIM + " "
                end_tag = ""
                blockquote_text = text[start:end]
                lines = blockquote_text.split("\n")
                last_length = 0
                for line in lines:
                    if len(line) == 0 and last_length == end:
                        continue
                    start_offset = start + last_length
                    last_length = last_length + len(line)
                    end_offset = start_offset + last_length
                    entities_offsets.append((start_tag, start_offset,))
                    entities_offsets.append((end_tag, end_offset,))
                    last_length = last_length + 1
                continue
            elif entity_type == TLEntityType.SPOILER:
                start_tag = end_tag = SPOILER_DELIM
            elif entity_type == TLEntityType.TEXT_LINK:
                url = entity.url
                start_tag = "["
                end_tag = f"]({url})"
            elif entity_type == TLEntityType.CUSTOM_EMOJI:
                emoji_id = entity.document_id
                start_tag = "!["
                end_tag = f"](tg://emoji?id={emoji_id})"
            else:
                continue

            entities_offsets.append((start_tag, start,))
            entities_offsets.append((end_tag, end,))

        entities_offsets = map(
            lambda x: x[1],
            sorted(
                enumerate(entities_offsets),
                key=lambda x: (x[1][1], x[0]),
                reverse=True
            )
        )

        for entity, offset in entities_offsets:
            text = text[:offset] + entity + text[offset:]

        return remove_surrogates(text)

##############################  END Markdown & HTML parsers   ##############################


def parse_text(text: str, parse_mode: Optional[str] = 'HTML', is_caption: bool = False) -> Dict[str, Any]:
    key = "message" if not is_caption else "caption"
    if not parse_mode:
        return {
            key: text,
            "entities": []
        }

    try:
        result = (Markdown() if parse_mode.lower() == "markdown" else HTML()).parse(text)

        return {
            key: result["message"],
            "entities": [i.to_tlrpc_object() for i in result["entities"]]
        }
    except Exception as e:
        raise e
