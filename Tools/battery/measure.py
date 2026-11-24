#!/usr/bin/env python3

import argparse
import os
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rails import GROUP_ORDER, average_power, grouped

DEVICE_DIR = "/data/local/tmp/battery_measure"
TRACE_DIR = "/data/misc/perfetto-traces"

DEVICE_SCRIPT = r"""#!/system/bin/sh
PKG=$1
CFG=$2
OUT=$3
DIR=$4
P=$(pidof $PKG)
U0=$(dumpsys power | grep -m1 -oE "mLastUserActivityTime[^=]*=[0-9]+")
F0=$(dumpsys window | grep -m1 -oE "mCurrentFocus=Window\{[^}]*\}")
[ -n "$P" ] && dumpsys gfxinfo $PKG reset > /dev/null
cat /proc/[0-9]*/stat > $DIR/procA.txt 2>/dev/null
[ -n "$P" ] && cat /proc/$P/task/*/stat > $DIR/threadsA.txt 2>/dev/null
cat $CFG | perfetto --txt -c - -o """ + TRACE_DIR + r"""/$OUT.pftrace > /dev/null 2>&1
[ -n "$P" ] && cat /proc/$P/task/*/stat > $DIR/threadsB.txt 2>/dev/null
cat /proc/[0-9]*/stat > $DIR/procB.txt 2>/dev/null
echo "pid=$P"
[ -n "$P" ] && dumpsys gfxinfo $PKG | grep -m1 "Total frames rendered"
U1=$(dumpsys power | grep -m1 -oE "mLastUserActivityTime[^=]*=[0-9]+")
F1=$(dumpsys window | grep -m1 -oE "mCurrentFocus=Window\{[^}]*\}")
[ "$U0" = "$U1" ] && echo "touch=no" || echo "touch=yes"
echo "focus=${F1#*u0 }"
[ "$F0" != "$F1" ] && echo "focusChanged=yes"
"""

CONFIG = """buffers { size_kb: 16384 fill_policy: DISCARD }
data_sources {
  config {
    name: "android.power"
    android_power_config {
      battery_poll_ms: 250
      collect_power_rails: true
    }
  }
}
duration_ms: %d
"""


def adb(args, serial, capture=True):
    cmd = ["adb"] + (["-s", serial] if serial else []) + args
    return subprocess.run(cmd, capture_output=capture, text=True).stdout


def load_stat(path):
    result = {}
    for line in open(path, errors="replace"):
        open_paren = line.find("(")
        close_paren = line.rfind(")")
        if open_paren < 0 or close_paren < 0:
            continue
        rest = line[close_paren + 2:].split()
        if len(rest) < 13:
            continue
        result[line[:open_paren].strip()] = (int(rest[11]) + int(rest[12]), line[open_paren + 1:close_paren].replace(" ", "_"))
    return result


def delta_by_name(before, after):
    result = {}
    for key, (ticks, name) in after.items():
        if key in before and before[key][1] == name:
            diff = ticks - before[key][0]
            if diff > 0:
                result[name] = result.get(name, 0) + diff
    return result


def run_window(args, index):
    name = "measure_%d_%d" % (int(time.time()), index)
    output = adb(["shell", "sh", DEVICE_DIR + "/measure.sh", args.package, DEVICE_DIR + "/config.pbtx", name, DEVICE_DIR], args.serial)
    info = dict(line.split("=", 1) for line in output.strip().splitlines() if "=" in line and not line.startswith("Total"))
    frames = None
    for line in output.splitlines():
        if line.startswith("Total frames rendered"):
            frames = int(line.split(":")[1])
    with tempfile.TemporaryDirectory() as tmp:
        trace = os.path.join(tmp, "trace.pftrace")
        adb(["pull", "%s/%s.pftrace" % (TRACE_DIR, name), trace], args.serial)
        files = {}
        for part in ("threadsA", "threadsB", "procA", "procB"):
            files[part] = os.path.join(tmp, part + ".txt")
            adb(["pull", "%s/%s.txt" % (DEVICE_DIR, part), files[part]], args.serial)
        if args.keep_trace:
            kept = os.path.abspath(name + ".pftrace")
            os.replace(trace, kept)
            trace = kept
        rails = average_power(trace) if os.path.exists(trace) else {}
        threads = {}
        if info.get("pid") and os.path.exists(files["threadsA"]) and os.path.exists(files["threadsB"]):
            threads = delta_by_name(load_stat(files["threadsA"]), load_stat(files["threadsB"]))
        processes = {}
        if os.path.exists(files["procA"]) and os.path.exists(files["procB"]):
            processes = delta_by_name(load_stat(files["procA"]), load_stat(files["procB"]))
    adb(["shell", "rm", "-f", "%s/%s.pftrace" % (TRACE_DIR, name)], args.serial)
    seconds = max((value[2] for value in rails.values()), default=args.seconds)
    return {
        "info": info,
        "frames": frames,
        "rails": rails,
        "groups": grouped(rails),
        "seconds": seconds,
        "threads": threads,
        "processes": processes,
    }


def report(result, args, label):
    info = result["info"]
    seconds = result["seconds"]
    print("== %s: %s, %.1fs" % (label, args.package, seconds))
    flags = []
    if not info.get("pid"):
        flags.append("APP NOT RUNNING")
    if info.get("touch") == "yes":
        flags.append("TOUCHED - discard this window")
    if info.get("focusChanged") == "yes":
        flags.append("FOCUS CHANGED")
    print("focus: %s%s" % (info.get("focus", "?"), ("  [" + ", ".join(flags) + "]") if flags else ""))
    if result["frames"] is not None:
        print("frames: %d (%.1f fps)" % (result["frames"], result["frames"] / seconds))
    groups = result["groups"]
    if groups:
        print("rails:  " + "  ".join("%s %.0f" % (key, groups[key]) for key in GROUP_ORDER if key in groups)
              + "  | TOTAL %.0f mW" % sum(groups.values()))
    ticks = sum(result["threads"].values())
    if result["threads"]:
        print("app CPU: %.2f s (%.0f%% of one core)" % (ticks / 100.0, ticks / seconds))
        top = sorted(result["threads"].items(), key=lambda item: -item[1])[:args.top]
        print("   " + ", ".join("%s %.2fs" % (name, value / 100.0) for name, value in top))
    top = sorted(result["processes"].items(), key=lambda item: -item[1])[:args.top]
    if top:
        print("system: " + ", ".join("%s %.2fs" % (name, value / 100.0) for name, value in top))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--package", default="com.exteragram.messenger")
    parser.add_argument("--seconds", type=int, default=20, help="window length")
    parser.add_argument("--repeat", type=int, default=1, help="windows to run back to back, then average")
    parser.add_argument("--top", type=int, default=8, help="threads and processes to list")
    parser.add_argument("--serial", help="adb device serial")
    parser.add_argument("--keep-trace", action="store_true", help="keep the Perfetto trace in the current directory")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        script = os.path.join(tmp, "measure.sh")
        config = os.path.join(tmp, "config.pbtx")
        with open(script, "w") as f:
            f.write(DEVICE_SCRIPT)
        with open(config, "w") as f:
            f.write(CONFIG % (args.seconds * 1000))
        adb(["shell", "mkdir", "-p", DEVICE_DIR], args.serial)
        adb(["push", script, DEVICE_DIR + "/measure.sh"], args.serial)
        adb(["push", config, DEVICE_DIR + "/config.pbtx"], args.serial)

    results = []
    try:
        for index in range(args.repeat):
            result = run_window(args, index)
            results.append(result)
            report(result, args, "window %d/%d" % (index + 1, args.repeat))
    finally:
        adb(["shell", "rm", "-rf", DEVICE_DIR], args.serial)

    clean = [r for r in results if r["info"].get("touch") != "yes"]
    if len(clean) > 1:
        seconds = sum(r["seconds"] for r in clean)
        frames = sum(r["frames"] or 0 for r in clean)
        groups = {}
        for r in clean:
            for key, value in r["groups"].items():
                groups[key] = groups.get(key, 0.0) + value / len(clean)
        ticks = sum(sum(r["threads"].values()) for r in clean)
        print("== average of %d untouched windows" % len(clean))
        print("frames: %.1f fps  app CPU: %.0f%% of one core" % (frames / seconds, ticks / seconds))
        print("rails:  " + "  ".join("%s %.0f" % (key, groups[key]) for key in GROUP_ORDER if key in groups)
              + "  | TOTAL %.0f mW" % sum(groups.values()))


if __name__ == "__main__":
    main()
