#!/usr/bin/env python3

import argparse
import collections
import re

HEX = re.compile(r"0x[0-9a-fA-F]+")
HASH = re.compile(r"@[0-9a-fA-F]+\b")
NUM = re.compile(r"(?<![A-Za-z_])-?\d+(\.\d+)?")
PATHS = re.compile(r"/storage/\S+|/data/\S+")
FILE_NAME = re.compile(r"\b\S+\.(mp4|MP4|jpg|JPG|png|webp|tgs|webm|temp|pt|enc|ogg|mp3|pdf|apk|zip)\b")
QUOTED = re.compile(r"'[^']*'|\"[^\"]*\"")

JAVA_LINE = re.compile(r"^\d\d_\d\d_\d{4}_(\d\d)_\d\d_\d\d\.\d+ ([A-Z])/tmessages: ?(.*)$")
NET_LINE = re.compile(r"^\s*\d+-\d+ (\d\d):\d\d:\d\d\.\d+ (\w+): (.*)$")
SEND_REQUEST = re.compile(r"send request (?:[\w.]+\.)?(?:\w+\$)?(\w+)@[0-9a-f]+")
GOT_ERROR = re.compile(r"(?:[\w.]+\.)?(?:\w+\$)?(\w+)@[0-9a-f]+ got error (-?\d+) (\S+)")
EXCEPTION = re.compile(r"^\s*([a-zA-Z_$][\w$.]*(?:Exception|Error))\b")
NET_ERROR = re.compile(r"rpc error (-?\d+): (\S+)")


def normalize(text):
    text = PATHS.sub("<path>", text)
    text = FILE_NAME.sub("<file>", text)
    text = HASH.sub("@H", text)
    text = HEX.sub("0xH", text)
    text = QUOTED.sub("<q>", text)
    text = NUM.sub("N", text)
    return text[:170]


def is_net_log(path):
    if path.endswith("_net.txt"):
        return True
    with open(path, errors="replace") as f:
        for _ in range(20):
            line = f.readline()
            if NET_LINE.match(line):
                return True
            if JAVA_LINE.match(line):
                return False
    return False


def print_hours(title, per_hour, keys):
    if not keys:
        return
    hours = sorted({hour for key in keys for hour in per_hour[key]})
    print("-- %s per hour:" % title)
    print("hour " + " ".join("%14s" % key[:14] for key in keys))
    for hour in hours:
        print("  %s " % hour + " ".join("%14d" % per_hour[key][hour] for key in keys))


def analyze_java(path, top, hours):
    templates = collections.Counter()
    template_hours = collections.defaultdict(set)
    levels = collections.Counter()
    requests = collections.Counter()
    request_hours = collections.defaultdict(collections.Counter)
    errors = collections.Counter()
    error_hours = collections.defaultdict(collections.Counter)
    exceptions = collections.Counter()
    lines = 0
    last_hour = "--"
    for line in open(path, errors="replace"):
        match = JAVA_LINE.match(line)
        if not match:
            exception = EXCEPTION.match(line)
            if exception:
                exceptions[exception.group(1)] += 1
            continue
        lines += 1
        hour, level, message = match.groups()
        last_hour = hour
        levels[level] += 1
        template = level + " " + normalize(message)
        templates[template] += 1
        template_hours[template].add(hour)
        request = SEND_REQUEST.search(message)
        if request:
            requests[request.group(1)] += 1
            request_hours[request.group(1)][hour] += 1
        error = GOT_ERROR.search(message)
        if error:
            key = "%s %s %s" % (error.group(1), error.group(2), error.group(3))
            errors[key] += 1
            error_hours[key][hour] += 1
        exception = EXCEPTION.match(message)
        if exception:
            exceptions[exception.group(1)] += 1
    print("== %s: %d lines, levels %s, last hour %s" % (path, lines, dict(levels), last_hour))
    print("-- top templates [hours seen]:")
    for template, count in templates.most_common(top):
        print("%9d  [%2d h]  %s" % (count, len(template_hours[template]), template))
    print("-- requests sent (peak per hour):")
    for request, count in requests.most_common(top):
        print("%9d  %-45s peak %d/h" % (count, request, max(request_hours[request].values())))
    print("-- RPC errors by request (peak per hour):")
    for key, count in errors.most_common(top):
        print("%9d  %-70s peak %d/h" % (count, key, max(error_hours[key].values())))
    if exceptions:
        print("-- exceptions:")
        for name, count in exceptions.most_common(top):
            print("%9d  %s" % (count, name))
    if hours:
        print_hours("requests", request_hours, [key for key, _ in requests.most_common(6)])
        print_hours("errors", error_hours, [key for key, _ in errors.most_common(4)])


def analyze_net(path, top, hours):
    templates = collections.Counter()
    template_hours = collections.defaultdict(set)
    errors = collections.Counter()
    error_hours = collections.defaultdict(collections.Counter)
    objects = collections.Counter()
    connects = collections.defaultdict(collections.Counter)
    lines = 0
    for line in open(path, errors="replace"):
        match = NET_LINE.match(line)
        if not match:
            continue
        lines += 1
        hour, level, message = match.groups()
        template = level + " " + normalize(message)
        templates[template] += 1
        template_hours[template].add(hour)
        if level == "error" and message.startswith("request "):
            error = NET_ERROR.search(message)
            if error:
                key = error.group(1) + " " + error.group(2)
                errors[key] += 1
                error_hours[key][hour] += 1
        received = re.search(r"received object \d*(TL_\w+)", message)
        if received:
            objects[received.group(1)] += 1
        if ") connected to " in message:
            connects["connected"][hour] += 1
    print("== %s: %d lines" % (path, lines))
    print("-- top templates [hours seen]:")
    for template, count in templates.most_common(top):
        print("%9d  [%2d h]  %s" % (count, len(template_hours[template]), template))
    print("-- RPC errors (per failed request, peak per hour):")
    for key, count in errors.most_common(top):
        print("%9d  %-40s peak %d/h" % (count, key, max(error_hours[key].values())))
    print("-- received objects:")
    for name, count in objects.most_common(min(top, 15)):
        print("%9d  %s" % (count, name))
    if hours:
        print_hours("new connections", connects, ["connected"])
        print_hours("errors", error_hours, [key for key, _ in errors.most_common(4)])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("log", help="FileLog file pulled from the device")
    parser.add_argument("--top", type=int, default=30, help="rows per section")
    parser.add_argument("--hours", action="store_true", help="add per-hour tables for the busiest requests and errors")
    args = parser.parse_args()
    if is_net_log(args.log):
        analyze_net(args.log, args.top, args.hours)
    else:
        analyze_java(args.log, args.top, args.hours)


if __name__ == "__main__":
    main()
