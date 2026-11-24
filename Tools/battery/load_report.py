#!/usr/bin/env python3

import argparse
import datetime
import json
import sys

STATES = ["foreground", "background", "screenOff"]
SYSTEM_WAKELOCKS = ("AudioMix", "AudioSpatial", "AudioDirectOut", "AudioOffload", "AudioIn", "*alarm*", "*job*",
                    "*sync*", "GOOGLE_C2DM", "NotificationManagerService", "*launch*", "WindowManager", "*vibrator*",
                    "*backup*", "wake:com.google.firebase")
KEY_MEASUREMENTS = [
    ("MEASUREMENT_CPU_POWER_MAMS", "CPU energy", "mah"),
    ("MEASUREMENT_USER_CPU_TIME_MS", "CPU user time", "ms"),
    ("MEASUREMENT_SYSTEM_CPU_TIME_MS", "CPU system time", "ms"),
    ("MEASUREMENT_MOBILE_POWER_MAMS", "Mobile radio energy", "mah"),
    ("MEASUREMENT_WIFI_POWER_MAMS", "Wi-Fi energy", "mah"),
    ("MEASUREMENT_MOBILE_RX_BYTES", "Mobile received", "bytes"),
    ("MEASUREMENT_MOBILE_TX_BYTES", "Mobile sent", "bytes"),
    ("MEASUREMENT_MOBILE_RX_PACKETS", "Mobile packets in", "count"),
    ("MEASUREMENT_MOBILE_TX_PACKETS", "Mobile packets out", "count"),
    ("MEASUREMENT_WIFI_RX_BYTES", "Wi-Fi received", "bytes"),
    ("MEASUREMENT_WIFI_TX_BYTES", "Wi-Fi sent", "bytes"),
    ("MEASUREMENT_WIFI_RX_PACKETS", "Wi-Fi packets in", "count"),
    ("MEASUREMENT_WIFI_TX_PACKETS", "Wi-Fi packets out", "count"),
]
KEY_TIMERS = [
    ("TIMER_MOBILE_RADIO_ACTIVE", "Mobile radio active", 1000),
    ("TIMER_PROCESS_STATE_TOP_MS", "State: top", 1),
    ("TIMER_PROCESS_STATE_TOP_SLEEPING_MS", "State: top sleeping", 1),
    ("TIMER_PROCESS_STATE_FOREGROUND_SERVICE_MS", "State: foreground service", 1),
    ("TIMER_PROCESS_STATE_FOREGROUND_MS", "State: foreground", 1),
    ("TIMER_PROCESS_STATE_BACKGROUND_MS", "State: background", 1),
    ("TIMER_PROCESS_STATE_CACHED_MS", "State: cached", 1),
    ("TIMER_AUDIO", "Audio", 1),
    ("TIMER_VIDEO", "Video", 1),
    ("TIMER_CAMERA", "Camera", 1),
    ("TIMER_GPS_SENSOR", "GPS", 1),
]
TIMER_MAPS = [
    ("TIMERS_WAKELOCKS_PARTIAL", "Partial wakelocks"),
    ("TIMERS_JOBS", "Jobs"),
    ("TIMERS_SYNCS", "Syncs"),
    ("TIMERS_SENSORS", "Sensors"),
]


def duration(ms):
    ms = int(round(ms))
    if ms < 1000:
        return "%dms" % ms
    seconds = ms // 1000
    if seconds < 60:
        return "%ds" % seconds
    if seconds < 3600:
        return "%dm %ds" % (seconds // 60, seconds % 60)
    return "%dh %dm" % (seconds // 3600, seconds % 3600 // 60)


def size(value):
    value = float(value)
    for unit in ("B", "KB", "MB", "GB"):
        if abs(value) < 1024 or unit == "GB":
            return ("%.0f %s" if unit == "B" else "%.1f %s") % (value, unit)
        value /= 1024


def percent(cpu, wall):
    return "%.1f%%" % (cpu * 100.0 / wall) if wall else "-"


def when(ms):
    return datetime.datetime.fromtimestamp(ms / 1000.0).strftime("%Y-%m-%d %H:%M:%S")


def value_text(value, kind):
    if kind == "mah":
        return "%.2f mAh" % (value / 3600000.0)
    if kind == "ms":
        return duration(value)
    if kind == "bytes":
        return size(value)
    return "%d" % value


def is_system_wakelock(name):
    return any(name.startswith(prefix) for prefix in SYSTEM_WAKELOCKS)


def monitor_disagreement(start_measurements, end_measurements, monitor):
    def delta(*keys):
        return sum(end_measurements.get(key, 0) - start_measurements.get(key, 0) for key in keys)

    own_cpu = sum(state["cpuMs"] for state in monitor["states"].values())
    own_rx = sum(state["rxBytes"] for state in monitor["states"].values())
    health_cpu = delta("MEASUREMENT_USER_CPU_TIME_MS", "MEASUREMENT_SYSTEM_CPU_TIME_MS")
    health_rx = delta("MEASUREMENT_WIFI_RX_BYTES", "MEASUREMENT_MOBILE_RX_BYTES")
    off = []
    if abs(health_cpu - own_cpu) > own_cpu * 0.1:
        off.append("CPU %s vs %s" % (duration(health_cpu), duration(own_cpu)))
    if abs(health_rx - own_rx) > own_rx * 0.1:
        off.append("received %s vs %s" % (size(health_rx), size(own_rx)))
    if off:
        print("   these deltas disagree with the monitor's own counters (%s):" % ", ".join(off))
        print("   batterystats counts only time on battery and flushes earlier work at the unplug boundary,")
        print("   so for this window read it for wakelocks, jobs and sensors, not for CPU or traffic")


def health_delta(start, end, title, top, monitor=None):
    start_measurements = (start or {}).get("measurements", {})
    end_measurements = end.get("measurements", {})
    on_battery = end_measurements.get("MEASUREMENT_REALTIME_BATTERY_MS", 0) - start_measurements.get("MEASUREMENT_REALTIME_BATTERY_MS", 0)
    print("-- %s (on battery %s)" % (title, duration(on_battery)))
    if start is not None and on_battery <= 0:
        print("   unchanged: battery stats only count time on battery, and the phone stayed plugged in")
        return
    if monitor:
        monitor_disagreement(start_measurements, end_measurements, monitor)
    skipped_energy = False
    for key, label, kind in KEY_MEASUREMENTS:
        if key not in end_measurements:
            continue
        if kind == "mah" and end_measurements[key] == 0:
            skipped_energy = True
            continue
        print("   %-24s %s" % (label, value_text(end_measurements[key] - start_measurements.get(key, 0), kind)))
    if skipped_energy:
        print("   %-24s not reported by this Android version" % "Energy estimates")
    start_timers = (start or {}).get("timers", {})
    end_timers = end.get("timers", {})
    for key, label, scale in KEY_TIMERS:
        if key in end_timers:
            count = end_timers[key][0] - start_timers.get(key, [0, 0])[0]
            time = (end_timers[key][1] - start_timers.get(key, [0, 0])[1]) / scale
            if count or time:
                print("   %-24s %s (%dx)" % (label, duration(time), count))
    start_maps = (start or {}).get("timerMaps", {})
    end_maps = end.get("timerMaps", {})
    for key, label in TIMER_MAPS:
        entries = []
        for name, (count, time) in end_maps.get(key, {}).items():
            old = start_maps.get(key, {}).get(name, [0, 0])
            if time - old[1] > 0 or count - old[0] > 0:
                entries.append((time - old[1], count - old[0], name))
        if entries:
            print("   %s:" % label)
            for time, count, name in sorted(entries, reverse=True)[:top]:
                marker = " (system)" if key == "TIMERS_WAKELOCKS_PARTIAL" and is_system_wakelock(name) else ""
                print("      %-12s %5dx  %s%s" % (duration(time), count, name, marker))


def threads_table(threads, top):
    for thread in threads[:top]:
        extra = ""
        if "foreground" in thread:
            extra = "   fg %8s / bg %8s / off %8s" % tuple(duration(thread.get(state, 0)) for state in STATES)
        if thread.get("at"):
            extra += "   %s" % thread["at"]
        print("   %-24s %10s%s" % (thread["name"], duration(thread["cpuMs"]), extra))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("report", help="load_*.json")
    parser.add_argument("--top", type=int, default=12, help="rows per section")
    parser.add_argument("--samples", type=int, default=24, help="timeline rows to show, newest last")
    args = parser.parse_args()
    report = json.load(open(args.report))
    if report.get("format") != 1:
        sys.exit("unknown report format %s" % report.get("format"))

    app, device, settings, process = report["app"], report["device"], report["settings"], report["process"]
    print("== %s report, %s" % (report["trigger"], when(report["createdAt"])))
    print("app %s (%s%s), %s %s, Android SDK %s, %d cores" % (
        app["version"], app["code"], ", debug" if app.get("debug") else "", device["manufacturer"], device["model"],
        device["sdk"], device["cores"]))
    print("accounts %s, proxy %s, logs %s, power save %s, battery optimization %s" % (
        settings["accounts"], settings["proxy"], settings["logs"], settings["powerSave"],
        "off" if settings["ignoringBatteryOptimizations"] else "on"))
    battery = report.get("battery")
    if battery and "plugged" in battery:
        print("battery %s%%, %s" % (battery.get("level", "?"), "plugged in" if battery["plugged"] else "on battery"))
    print("-- process: up %s, %s CPU (%s of one core), now %s; threads alive at capture:" % (
        duration(process["uptimeMs"]), duration(process["cpuMs"]), percent(process["cpuMs"], process["uptimeMs"]), process["state"]))
    threads_table(process["threads"], args.top)

    monitor = report.get("monitor")
    if monitor:
        header = "-- monitor since %s" % when(monitor["startedAt"])
        start_battery = monitor.get("batteryAtStart")
        if start_battery and "plugged" in start_battery:
            header += ", battery %s%% %s at start" % (start_battery.get("level", "?"), "plugged in" if start_battery["plugged"] else "unplugged")
        sampled = [sample for sample in monitor["samples"] if "plugged" in sample]
        if sampled:
            header += ", on battery for %s of %s sampled" % (
                duration(sum(sample["wallMs"] for sample in sampled if not sample["plugged"])),
                duration(sum(sample["wallMs"] for sample in sampled)))
        print(header + ":")
        print("   %-11s %10s %10s %8s %10s %10s" % ("state", "time", "CPU", "of core", "received", "sent"))
        for state in STATES:
            s = monitor["states"][state]
            print("   %-11s %10s %10s %8s %10s %10s" % (state, duration(s["wallMs"]), duration(s["cpuMs"]),
                                                     percent(s["cpuMs"], s["wallMs"]), size(s["rxBytes"]), size(s["txBytes"])))
        print("-- busiest threads while monitored (includes threads that have since exited):")
        threads_table(monitor["threads"], args.top)

    episode = report.get("episode")
    if episode:
        print("-- background episode since %s: %s, %s CPU (%s of one core), received %s, sent %s" % (
            when(episode["startedAt"]), duration(episode["wallMs"]), duration(episode["cpuMs"]),
            percent(episode["cpuMs"], episode["wallMs"]),
            size(episode["rxBytes"]) if episode["rxBytes"] >= 0 else "?",
            size(episode["txBytes"]) if episode["txBytes"] >= 0 else "?"))
        threads_table(episode["threads"], args.top)

    if monitor and monitor["samples"] and args.samples > 0:
        print("-- timeline (last %d samples, rows longer than %s are Doze gaps):" % (
            min(args.samples, len(monitor["samples"])), duration(monitor["sampleIntervalMs"])))
        for sample in monitor["samples"][-args.samples:]:
            busiest = ", ".join("%s %s" % (t["name"], duration(t["cpuMs"])) for t in sample["threads"])
            print("   %s%s %-10s %8s %7s  ↓%-9s ↑%-9s %s" % (
                when(sample["t"])[11:], "⚡" if sample.get("plugged") else " ",
                sample["state"], duration(sample["wallMs"]),
                percent(sample["cpuMs"], sample["wallMs"]), size(sample["rxBytes"]), size(sample["txBytes"]), busiest))

    now = report.get("healthNow")
    if now:
        if report.get("healthEpisodeStart"):
            health_delta(report["healthEpisodeStart"], now, "battery stats during the background episode", args.top)
        if report.get("healthStart"):
            health_delta(report["healthStart"], now, "battery stats while monitored", args.top, monitor)
        else:
            health_delta(None, now, "battery stats since the last full charge", args.top)


if __name__ == "__main__":
    main()
