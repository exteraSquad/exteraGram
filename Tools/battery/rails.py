#!/usr/bin/env python3

import argparse
import sys

TRACE_PACKET = 1
POWER_RAILS = 40
RAIL_DESCRIPTOR = 1
ENERGY_DATA = 2

GROUPS = {
    "DDR": "Memory",
    "MIF": "Memory",
    "MODEM": "Cellular",
    "Cellular": "Cellular",
    "GPU": "GPU",
    "Display": "Display",
    "WLAN-BT": "WLAN-BT",
}
GROUP_ORDER = ["CPU(LITTLE)", "CPU(MID)", "CPU(BIG)", "GPU", "Display", "Memory", "Cellular", "WLAN-BT", "Other"]


def _varint(buf, i):
    shift = 0
    result = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if b < 0x80:
            return result, i
        shift += 7


def _fields(buf):
    i = 0
    n = len(buf)
    while i < n:
        key, i = _varint(buf, i)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, i = _varint(buf, i)
        elif wire == 1:
            value = buf[i:i + 8]
            i += 8
        elif wire == 2:
            length, i = _varint(buf, i)
            value = buf[i:i + length]
            i += length
        elif wire == 5:
            value = buf[i:i + 4]
            i += 4
        else:
            raise ValueError("unsupported wire type %d" % wire)
        yield number, wire, value


def read_rails(path):
    data = open(path, "rb").read()
    names = {}
    samples = {}
    for number, wire, packet in _fields(data):
        if number != TRACE_PACKET or wire != 2:
            continue
        for pnumber, pwire, rails in _fields(packet):
            if pnumber != POWER_RAILS or pwire != 2:
                continue
            for rnumber, rwire, value in _fields(rails):
                if rwire != 2:
                    continue
                fields = {f: v for f, _, v in _fields(value)}
                if rnumber == RAIL_DESCRIPTOR:
                    names[fields.get(1, 0)] = (fields.get(2, b"").decode(), fields.get(3, b"").decode())
                elif rnumber == ENERGY_DATA:
                    samples.setdefault(fields.get(1, 0), []).append((fields.get(2, 0), fields.get(3, 0)))
    return names, samples


def average_power(path):
    names, samples = read_rails(path)
    result = {}
    for index, points in samples.items():
        points.sort()
        if len(points) < 2:
            continue
        (t0, e0), (t1, e1) = points[0], points[-1]
        if t1 <= t0:
            continue
        name, subsystem = names.get(index, ("rail%d" % index, "?"))
        result[name] = (subsystem, (e1 - e0) / (t1 - t0), (t1 - t0) / 1000.0)
    return result


def group_of(subsystem):
    if subsystem.startswith("CPU"):
        return subsystem
    return GROUPS.get(subsystem, "Other")


def grouped(rails):
    groups = {}
    for subsystem, mw, _ in rails.values():
        key = group_of(subsystem)
        groups[key] = groups.get(key, 0.0) + mw
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", help="Perfetto trace with power rails")
    parser.add_argument("--groups", action="store_true", help="sum rails into CPU/GPU/Display/Memory/Cellular groups")
    args = parser.parse_args()
    rails = average_power(args.trace)
    if not rails:
        sys.exit("no power rails in %s" % args.trace)
    if args.groups:
        groups = grouped(rails)
        for key in GROUP_ORDER:
            if key in groups:
                print("%-12s %8.1f mW" % (key, groups[key]))
    else:
        for name, (subsystem, mw, seconds) in sorted(rails.items(), key=lambda item: -item[1][1]):
            print("%-22s %-12s %8.1f mW  (%.1fs)" % (name, subsystem, mw, seconds))
    print("%-12s %8.1f mW" % ("TOTAL", sum(mw for _, mw, _ in rails.values())))


if __name__ == "__main__":
    main()
