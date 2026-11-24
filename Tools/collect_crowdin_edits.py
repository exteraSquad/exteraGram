#!/usr/bin/env python3

import argparse
import re
from pathlib import Path

from cleanup_crowdin_translations import (
    NAME_PATTERN,
    RESOURCE_PATTERN,
    display_path,
    find_invalid_resources,
    parse_resources,
)


RESOURCES_START_PATTERN = re.compile(r"<resources\b[^>]*>")


def raw_resources(text: str) -> dict[tuple[str, str], str]:
    resources = {}

    for match in RESOURCE_PATTERN.finditer(text):
        name = NAME_PATTERN.search(match.group(2))
        if name:
            resources[(match.group(1), name.group(1))] = match.group()

    return resources


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("translations", nargs="+", type=Path)
    args = parser.parse_args()

    source_resources = parse_resources(args.source)

    for translation_path in args.translations:
        baseline_path = args.baseline / translation_path.parent.name / translation_path.name
        if not baseline_path.is_file():
            continue

        text = translation_path.read_text(encoding="utf-8")
        local = raw_resources(text)
        baseline = raw_resources(baseline_path.read_text(encoding="utf-8"))

        removed = sorted(name for _, name in baseline.keys() - local.keys())
        if removed:
            print(
                f"Removed locally, kept in Crowdin: {display_path(translation_path)}: "
                f"{', '.join(removed)}"
            )

        edited = {key for key, resource in local.items() if baseline.get(key) != resource}
        if not edited:
            continue

        translation_resources = parse_resources(translation_path)
        same_as_source = {
            key
            for key in edited
            if key in source_resources
            and translation_resources[key][0] == source_resources[key][0]
        }
        invalid = find_invalid_resources(
            source_resources,
            {key: translation_resources[key] for key in edited - same_as_source},
        )
        if invalid:
            print(
                f"Skipped invalid edits: {display_path(translation_path)}: "
                f"{', '.join(sorted(name for _, name in invalid))}"
            )

        edited -= invalid
        if not edited:
            continue

        header = RESOURCES_START_PATTERN.search(text)
        output_path = args.output / translation_path.parent.name / translation_path.name
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            f"{header.group() if header else '<resources>'}\n"
            + "".join(f"    {resource}\n" for key, resource in local.items() if key in edited)
            + "</resources>\n",
            encoding="utf-8",
        )
        print(
            f"Uploading {len(edited)} edited translations: {display_path(translation_path)}: "
            f"{', '.join(sorted(name for _, name in edited))}"
        )


if __name__ == "__main__":
    main()
