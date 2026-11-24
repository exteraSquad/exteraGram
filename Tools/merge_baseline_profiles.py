"""Merges the per-test profiles produced on device into the two files the build consumes.

    startup-prof.txt   = rules from the test marked includeInStartupProfile
    baseline-prof.txt  = those rules plus the ones from every other test

Rule ordering matches androidx.benchmark.macro.ProfileRule.comparator.
"""

import re
import sys
from pathlib import Path

RULE = re.compile(r"(H?S?P?)L([^;]*);(->)?(.*)")


def sort_key(rule):
    match = RULE.match(rule)
    if match is None:
        return ("", "")
    class_descriptor = match.group(2)
    method_descriptor = match.group(4) if match.group(3) else ""
    return (class_descriptor, method_descriptor)


def read_rules(paths):
    rules = set()
    for path in paths:
        for line in path.read_text().splitlines():
            if line.strip():
                rules.add(line)
    return rules


def write_rules(path, rules):
    path.write_text("\n".join(sorted(rules, key=sort_key)) + "\n")


def main():
    if len(sys.argv) != 3:
        print("usage: merge_baseline_profiles.py <pulled-dir> <output-dir>", file=sys.stderr)
        return 2

    pulled, output = Path(sys.argv[1]), Path(sys.argv[2])

    # The library also writes a timestamped copy of every profile; ignore those.
    startup = [p for p in pulled.rglob("*-startup-prof.txt") if not re.search(r"-\d{4}", p.stem)]
    journey = [p for p in pulled.rglob("*-baseline-prof.txt") if not re.search(r"-\d{4}", p.stem)]

    if not startup and not journey:
        print(f"no profiles found under {pulled}", file=sys.stderr)
        return 1
    if not startup:
        print("no startup profile found; startup-prof.txt would be empty", file=sys.stderr)
        return 1

    startup_rules = read_rules(startup)
    write_rules(output / "startup-prof.txt", startup_rules)
    write_rules(output / "baseline-prof.txt", startup_rules | read_rules(journey))

    print(f"startup-prof.txt: {len(startup_rules)} rules")
    print(f"baseline-prof.txt: {len(startup_rules | read_rules(journey))} rules")
    return 0


if __name__ == "__main__":
    sys.exit(main())
