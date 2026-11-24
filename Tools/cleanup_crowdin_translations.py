#!/usr/bin/env python3

import argparse
import os
import re
import xml.etree.ElementTree as ElementTree
from pathlib import Path


RESOURCE_TAGS = {"array", "plurals", "string", "string-array"}
RESOURCE_START_PATTERN = re.compile(
    r'^\s*<(array|plurals|string|string-array)\b[^>]*\bname="([^"]+)"[^>]*>'
)
RESOURCE_PATTERN = re.compile(
    r"<(array|plurals|string|string-array)\b([^>]*)>(.*?)</\1>", re.DOTALL
)
ITEM_PATTERN = re.compile(r"<item\b([^>]*)>(.*?)</item>", re.DOTALL)
MARKUP_PATTERN = re.compile(r"<!\[CDATA\[.*?\]\]>|<!--.*?-->|<[^>]*>", re.DOTALL)
NAME_PATTERN = re.compile(r'\bname="([^"]+)"')
PLACEHOLDER_PATTERN = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
LONE_ASTERISK_PATTERN = re.compile(r"(?<!\*)\*(?!\*)")
NOISE_PATTERN = re.compile(r"^[\s.!,;:·…_-]*$")
APOSTROPHE_ENTITIES = ("&apos;", "&#39;", "&#x27;", "&#X27;")
HEX_DIGITS = frozenset("0123456789abcdefABCDEF")
ZERO_WIDTH_SPACE = "\u200b"


def parse_resources(path: Path) -> dict[tuple[str, str], tuple[str, bool]]:
    root = ElementTree.parse(path).getroot()
    resources: dict[tuple[str, str], tuple[str, bool]] = {}

    for element in root:
        name = element.attrib.get("name")
        if element.tag not in RESOURCE_TAGS or not name:
            continue

        key = (element.tag, name)
        if key in resources:
            raise ValueError(f"Duplicate resource {name!r} in {path}")

        value = "".join(element.itertext()).strip()
        translatable = element.attrib.get("translatable") != "false"
        resources[key] = (value, translatable)

    return resources


def escape_text(text: str, in_quote: bool, in_cdata: bool) -> tuple[str, bool, bool]:
    output: list[str] = []
    valid = True
    index = 0

    while index < len(text):
        character = text[index]

        if character == "\\" and index + 1 < len(text):
            if text[index + 1] == "u":
                digits = text[index + 2 : index + 6]
                if len(digits) < 4 or not HEX_DIGITS.issuperset(digits):
                    valid = False
                output.append(text[index : index + 6])
                index += 6
            else:
                output.append(text[index : index + 2])
                index += 2
            continue

        if character == ZERO_WIDTH_SPACE:
            index += 1
            continue

        if character == '"':
            in_quote = not in_quote
        elif character == "'" and not in_quote:
            output.append("\\'")
            index += 1
            continue
        elif character == "&" and not in_cdata and not in_quote:
            entity = next(
                (item for item in APOSTROPHE_ENTITIES if text.startswith(item, index)),
                None,
            )
            if entity is not None:
                output.append("\\'")
                index += len(entity)
                continue

        output.append(character)
        index += 1

    return "".join(output), in_quote, valid


def escape_value(value: str) -> tuple[str, bool]:
    stripped = value.lstrip()
    if stripped[:1] in {"@", "?"}:
        offset = len(value) - len(stripped)
        value = f"{value[:offset]}\\{stripped}"

    output: list[str] = []
    in_quote = False
    valid = True
    position = 0

    for markup in MARKUP_PATTERN.finditer(value):
        text, in_quote, text_valid = escape_text(
            value[position : markup.start()], in_quote, False
        )
        output.append(text)
        valid = valid and text_valid

        token = markup.group()
        if token.startswith("<![CDATA["):
            inner, in_quote, inner_valid = escape_text(token[9:-3], in_quote, True)
            output.append(f"<![CDATA[{inner}]]>")
            valid = valid and inner_valid
        else:
            output.append(token)
            if not token.startswith("<!--"):
                in_quote = False

        position = markup.end()

    text, _, text_valid = escape_text(value[position:], in_quote, False)
    output.append(text)

    return "".join(output), valid and text_valid


def escape_items(body: str) -> tuple[str, bool]:
    output: list[str] = []
    valid = True
    position = 0

    for item in ITEM_PATTERN.finditer(body):
        output.append(body[position : item.start()])
        value, item_valid = escape_value(item.group(2))
        output.append(f"<item{item.group(1)}>{value}</item>")
        valid = valid and item_valid
        position = item.end()

    output.append(body[position:])
    return "".join(output), valid


def escape_resources(path: Path) -> tuple[int, set[tuple[str, str]]]:
    original = path.read_text(encoding="utf-8")
    broken_resources: set[tuple[str, str]] = set()
    escaped_count = 0

    def rewrite(match: re.Match[str]) -> str:
        nonlocal escaped_count

        tag, attributes, body = match.group(1), match.group(2), match.group(3)
        name = NAME_PATTERN.search(attributes)
        if not name or attributes.endswith("/") or 'xml:space="preserve"' in attributes:
            return match.group()

        if tag == "string":
            escaped, valid = escape_value(body)
        else:
            escaped, valid = escape_items(body)

        if not valid:
            broken_resources.add((tag, name.group(1)))
            return match.group()

        if escaped == body:
            return match.group()

        escaped_count += 1
        return f"<{tag}{attributes}>{escaped}</{tag}>"

    updated = RESOURCE_PATTERN.sub(rewrite, original)
    if updated != original:
        path.write_text(updated, encoding="utf-8")

    return escaped_count, broken_resources


def find_invalid_resources(
    source_resources: dict[tuple[str, str], tuple[str, bool]],
    translation_resources: dict[tuple[str, str], tuple[str, bool]],
) -> set[tuple[str, str]]:
    invalid_resources = set()

    for key, (translation, _) in translation_resources.items():
        source = source_resources.get(key)
        if source is None:
            invalid_resources.add(key)
            continue

        source_value, _ = source
        if (
            not translation
            or translation == source_value
            or NOISE_PATTERN.fullmatch(translation)
        ):
            invalid_resources.add(key)
            continue

        if sorted(PLACEHOLDER_PATTERN.findall(translation)) != sorted(
            PLACEHOLDER_PATTERN.findall(source_value)
        ):
            invalid_resources.add(key)
            continue

        if translation.count("**") % 2 or len(
            LONE_ASTERISK_PATTERN.findall(translation)
        ) > len(LONE_ASTERISK_PATTERN.findall(source_value)):
            invalid_resources.add(key)

    return invalid_resources


def remove_resources(path: Path, resources_to_remove: set[tuple[str, str]]) -> None:
    if not resources_to_remove:
        return

    output_lines = []
    skipped_tag = None

    for line in path.read_text(encoding="utf-8").splitlines(keepends=True):
        if skipped_tag is not None:
            if f"</{skipped_tag}>" in line:
                skipped_tag = None
            continue

        match = RESOURCE_START_PATTERN.match(line)
        if match is None or (match.group(1), match.group(2)) not in resources_to_remove:
            output_lines.append(line)
            continue

        tag = match.group(1)
        if f"</{tag}>" not in line and "/>" not in line:
            skipped_tag = tag

    if skipped_tag is not None:
        raise ValueError(f"Unclosed resource <{skipped_tag}> in {path}")

    path.write_text("".join(output_lines), encoding="utf-8")


def display_path(path: Path) -> str:
    return os.path.relpath(path, Path.cwd())


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--minimum-completion", required=True, type=int)
    parser.add_argument("translations", nargs="+", type=Path)
    args = parser.parse_args()

    if not 0 <= args.minimum_completion <= 100:
        parser.error("--minimum-completion must be between 0 and 100")

    source_resources = parse_resources(args.source)
    source_resource_count = sum(
        1 for _, translatable in source_resources.values() if translatable
    )

    for translation_path in args.translations:
        escaped_count, broken_resources = escape_resources(translation_path)

        if escaped_count:
            print(
                f"Escaped {escaped_count} translations: "
                f"{display_path(translation_path)}"
            )

        translation_resources = parse_resources(translation_path)
        invalid_resources = find_invalid_resources(
            source_resources, translation_resources
        )
        invalid_resources |= broken_resources

        if invalid_resources:
            remove_resources(translation_path, invalid_resources)
            translation_resources = parse_resources(translation_path)
            print(
                f"Removed {len(invalid_resources)} invalid translations: "
                f"{display_path(translation_path)}"
            )

        translated_resource_count = len(translation_resources)
        completion_percent = translated_resource_count * 100 // source_resource_count

        if (
            translated_resource_count * 100
            < source_resource_count * args.minimum_completion
        ):
            translation_path.unlink()
            try:
                translation_path.parent.rmdir()
            except OSError:
                pass
            print(
                f"Removed incomplete translation "
                f"({completion_percent}% < {args.minimum_completion}%): "
                f"{display_path(translation_path)}"
            )


if __name__ == "__main__":
    main()
