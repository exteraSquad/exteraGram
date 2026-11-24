#!/usr/bin/env python3
import argparse
import math
import re
import struct
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    import numpy as np
    import shapely
    from scipy.ndimage import distance_transform_edt, gaussian_filter, map_coordinates
    from shapely.geometry import Point, Polygon
    from shapely.ops import nearest_points
except ImportError as error:
    raise SystemExit(f"{error.name} is missing: pip install numpy scipy shapely")

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = ROOT / "TMessagesProj/src/main/res/drawable/send_extera_24.xml"
DEFAULT_OUTPUT = ROOT / "TMessagesProj/src/main/assets/models/extera_arrow.binobj"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def read_path_data(source):
    for node in ET.parse(source).iter("path"):
        data = node.get(ANDROID_NS + "pathData")
        if data:
            return data
    raise SystemExit(f"no pathData in {source}")


def tokenize(data):
    return re.findall(r"[MmLlCcZzHhVv]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", data)


def flatten(data, step):
    tokens = tokenize(data)
    points = []
    cursor = (0.0, 0.0)
    start = cursor
    command = None
    i = 0

    def take(count):
        nonlocal i
        values = [float(v) for v in tokens[i:i + count]]
        i += count
        return values

    def add_line(target):
        length = math.dist(cursor, target)
        segments = max(1, math.ceil(length / step))
        for k in range(1, segments + 1):
            t = k / segments
            points.append((cursor[0] + (target[0] - cursor[0]) * t, cursor[1] + (target[1] - cursor[1]) * t))

    def add_cubic(c1, c2, target):
        approx = math.dist(cursor, c1) + math.dist(c1, c2) + math.dist(c2, target)
        segments = max(8, math.ceil(approx / step))
        for k in range(1, segments + 1):
            t = k / segments
            u = 1 - t
            x = u * u * u * cursor[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * target[0]
            y = u * u * u * cursor[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * target[1]
            points.append((x, y))

    while i < len(tokens):
        token = tokens[i]
        if token.isalpha():
            command = token
            i += 1
            if command in "Zz":
                add_line(start)
                cursor = start
                continue
        relative = command.islower()
        base = cursor if relative else (0.0, 0.0)
        kind = command.upper()
        if kind == "M":
            x, y = take(2)
            cursor = (base[0] + x, base[1] + y)
            start = cursor
            points.append(cursor)
            command = "l" if relative else "L"
        elif kind == "L":
            x, y = take(2)
            target = (base[0] + x, base[1] + y)
            add_line(target)
            cursor = target
        elif kind == "H":
            (x,) = take(1)
            target = (base[0] + x, cursor[1])
            add_line(target)
            cursor = target
        elif kind == "V":
            (y,) = take(1)
            target = (cursor[0], base[1] + y)
            add_line(target)
            cursor = target
        elif kind == "C":
            x1, y1, x2, y2, x, y = take(6)
            c1 = (base[0] + x1, base[1] + y1)
            c2 = (base[0] + x2, base[1] + y2)
            target = (base[0] + x, base[1] + y)
            add_cubic(c1, c2, target)
            cursor = target
        else:
            raise SystemExit(f"unsupported command {command}")

    merge = step / 2
    cleaned = []
    for p in points:
        if not cleaned or math.dist(cleaned[-1], p) > merge:
            cleaned.append(p)
    while len(cleaned) > 3 and math.dist(cleaned[0], cleaned[-1]) <= merge:
        cleaned.pop()
    return cleaned


def resample(polygon, spacing):
    perimeter = [math.dist(a, b) for a, b in zip(polygon, polygon[1:] + polygon[:1])]
    total = sum(perimeter)
    count = max(3, round(total / spacing))
    result = []
    segment = 0
    walked = 0.0
    for k in range(count):
        target = total * k / count
        while walked + perimeter[segment] < target:
            walked += perimeter[segment]
            segment += 1
        a, b = polygon[segment], polygon[(segment + 1) % len(polygon)]
        t = (target - walked) / perimeter[segment] if perimeter[segment] else 0
        result.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return result


def signed_area(polygon):
    return 0.5 * sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(polygon, polygon[1:] + polygon[:1]))


def inside(polygon, x, y):
    result = False
    j = len(polygon) - 1
    for i in range(len(polygon)):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            result = not result
        j = i
    return result


def delaunay(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    span = max(max(xs) - min(xs), max(ys) - min(ys)) * 20
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    vertices = list(points) + [(cx - span, cy - span), (cx + span, cy - span), (cx, cy + span)]
    n = len(points)

    def circumcircle(a, b, c):
        ax, ay = vertices[a]
        bx, by = vertices[b]
        qx, qy = vertices[c]
        d = 2 * (ax * (by - qy) + bx * (qy - ay) + qx * (ay - by))
        ux = ((ax * ax + ay * ay) * (by - qy) + (bx * bx + by * by) * (qy - ay) + (qx * qx + qy * qy) * (ay - by)) / d
        uy = ((ax * ax + ay * ay) * (qx - bx) + (bx * bx + by * by) * (ax - qx) + (qx * qx + qy * qy) * (bx - ax)) / d
        return ux, uy, (ax - ux) ** 2 + (ay - uy) ** 2

    triangles = {(n, n + 1, n + 2): circumcircle(n, n + 1, n + 2)}
    order = sorted(range(n), key=lambda k: (points[k][0], points[k][1]))
    for index in order:
        px, py = vertices[index]
        bad = [t for t, (ux, uy, r2) in triangles.items() if (px - ux) ** 2 + (py - uy) ** 2 < r2]
        edges = {}
        for t in bad:
            for e in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
                key = tuple(sorted(e))
                edges[key] = edges.get(key, 0) + 1
            del triangles[t]
        for (a, b), shared in edges.items():
            if shared == 1:
                triangles[(a, b, index)] = circumcircle(a, b, index)
    return [t for t in triangles if max(t) < n]


def dome_field(core, step, ramp, blur):
    minx, miny, maxx, maxy = core.bounds
    margin = blur * 3 + step
    xs = np.arange(minx - margin, maxx + margin + step, step)
    ys = np.arange(miny - margin, maxy + margin + step, step)
    gx, gy = np.meshgrid(xs, ys)
    mask = shapely.contains_xy(core, gx, gy)
    t = np.clip(distance_transform_edt(mask, sampling=step) / ramp, 0, 1)
    field = gaussian_filter(t * t * (3 - 2 * t), blur / step)
    grad_y, grad_x = np.gradient(field, step)

    def sample(x, y):
        coords = [[(y - ys[0]) / step], [(x - xs[0]) / step]]
        return tuple(float(map_coordinates(f, coords, order=1)[0]) for f in (field, grad_x, grad_y))

    return sample


def build(outline, size, half_thickness, radius, dome, spacing):
    xs = [p[0] for p in outline]
    ys = [p[1] for p in outline]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    scale = size / max(max(xs) - min(xs), max(ys) - min(ys))
    polygon = [((x - cx) * scale, -(y - cy) * scale) for x, y in outline]
    if signed_area(polygon) < 0:
        polygon.reverse()
    minx, maxx = min(p[0] for p in polygon), max(p[0] for p in polygon)
    miny, maxy = min(p[1] for p in polygon), max(p[1] for p in polygon)
    span = max(maxx - minx, maxy - miny)

    core = Polygon(polygon).buffer(-radius, quad_segs=64)
    if core.geom_type != "Polygon":
        core = max(core.geoms, key=lambda g: g.area)
    silhouette = core.buffer(radius, quad_segs=64)
    shape = list(silhouette.exterior.coords)[:-1]
    if signed_area(shape) < 0:
        shape.reverse()
    boundary = resample(shape, spacing * 0.5)
    dome_shape = dome_field(core, spacing / 16, radius * 2.5, radius * 0.6)

    def surface(px, py):
        point = Point(px, py)
        distance = core.distance(point)
        if distance > 1e-9:
            nearest = nearest_points(core, point)[0]
            dx, dy = (px - nearest.x) / distance, (py - nearest.y) / distance
            distance = min(distance, radius)
            root = math.sqrt(max(0.0, radius * radius - distance * distance))
            return half_thickness - radius + root, (dx * distance / radius, dy * distance / radius, root / radius)
        h, hx, hy = dome_shape(px, py)
        normal = (-dome * hx, -dome * hy, 1.0)
        length = math.sqrt(sum(c * c for c in normal))
        return half_thickness + dome * h, tuple(c / length for c in normal)

    candidates = []
    rings = 7
    outer = silhouette.exterior
    for bx, by in boundary:
        nearest = nearest_points(core, Point(bx, by))[0]
        nx, ny = (nearest.x - bx) / radius, (nearest.y - by) / radius
        for ring in range(1, rings + 1):
            d = radius * (1 - math.cos(math.pi / 2 * ring / rings))
            candidates.append((bx + nx * d, by + ny * d))
    sminx, sminy, smaxx, smaxy = silhouette.bounds
    y = sminy
    row = 0
    while y <= smaxy:
        x = sminx + (spacing / 2 if row % 2 else 0)
        while x <= smaxx:
            candidates.append((x, y))
            x += spacing
        y += spacing * math.sqrt(3) / 2
        row += 1

    points = list(boundary)
    cell = spacing * 0.3
    grid = {}
    for index, (px, py) in enumerate(points):
        grid.setdefault((int(px // cell), int(py // cell)), []).append(index)
    for px, py in candidates:
        if not silhouette.contains(Point(px, py)):
            continue
        d = outer.distance(Point(px, py))
        minimum = spacing * 0.45 if d < radius else spacing * 0.55
        gx, gy = int(px // cell), int(py // cell)
        reach = int(minimum // cell) + 1
        close = False
        for ix in range(gx - reach, gx + reach + 1):
            for iy in range(gy - reach, gy + reach + 1):
                for other in grid.get((ix, iy), ()):
                    if math.dist(points[other], (px, py)) < minimum:
                        close = True
                        break
                if close:
                    break
            if close:
                break
        if close or d < 0.05:
            continue
        grid.setdefault((gx, gy), []).append(len(points))
        points.append((px, py))

    triangles = [t for t in delaunay(points) if inside(shape, *((points[t[0]][0] + points[t[1]][0] + points[t[2]][0]) / 3, (points[t[0]][1] + points[t[1]][1] + points[t[2]][1]) / 3))]
    triangles = [(a, b, c) if signed_area([points[a], points[b], points[c]]) > 0 else (a, c, b) for a, b, c in triangles]

    edge_count = {}
    for t in triangles:
        for e in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0])):
            key = tuple(sorted(e))
            edge_count[key] = edge_count.get(key, 0) + 1
    open_edges = [e for e, c in edge_count.items() if c == 1 and (e[0] >= len(boundary) or e[1] >= len(boundary))]
    if open_edges:
        raise SystemExit(f"{len(open_edges)} outline edges were not recovered; lower --spacing")

    vertices = []
    front = []
    back = []
    for index, (px, py) in enumerate(points):
        on_boundary = index < len(boundary)
        z, normal = surface(px, py)
        if on_boundary:
            z = 0.0
            normal = (normal[0], normal[1], 0.0)
            length = math.hypot(normal[0], normal[1])
            normal = (normal[0] / length, normal[1] / length, 0.0)
        uv = ((px - minx) / span, (py - miny) / span)
        front.append(len(vertices))
        vertices.append(((px, py, z), uv, normal))
        if on_boundary:
            back.append(front[-1])
        else:
            back.append(len(vertices))
            vertices.append(((px, py, -z), uv, (normal[0], normal[1], -normal[2])))

    faces = []
    for a, b, c in triangles:
        faces.append((front[a], front[b], front[c]))
        faces.append((back[a], back[c], back[b]))
    return vertices, faces


def bend(vertices, curvature):
    if curvature == 0:
        return vertices
    bent = []
    for (x, y, z), coords, (nx, ny, nz) in vertices:
        fx, fy = -2 * curvature * x, -2 * curvature * y
        normal = (nx - fx * nz, ny - fy * nz, nz)
        length = math.sqrt(sum(c * c for c in normal))
        bent.append(((x, y, z - curvature * (x * x + y * y)), coords, tuple(c / length for c in normal)))
    zs = [position[2] for position, _, _ in bent]
    shift = (max(zs) + min(zs)) / 2
    return [((x, y, z - shift), coords, normal) for (x, y, z), coords, normal in bent]


def write_binobj(path, vertices, triangles):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as out:
        def floats(values):
            out.write(struct.pack(">i", len(values)))
            out.write(struct.pack(f">{len(values)}f", *values))

        floats([c for position, _, _ in vertices for c in position])
        floats([u if k == 0 else 1 - u for _, coords, _ in vertices for k, u in enumerate(coords)])
        floats([c for _, _, normal in vertices for c in normal])
        out.write(struct.pack(">i", len(triangles) * 3))
        for triangle in triangles:
            for index in triangle:
                out.write(struct.pack(">iii", index, index, index))


def main():
    parser = argparse.ArgumentParser(description="Turn the exteraGram logo vector into a rounded GLIcon .binobj model.")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--size", type=float, default=64.0)
    parser.add_argument("--thickness", type=float, default=11.0)
    parser.add_argument("--radius", type=float, default=5.5)
    parser.add_argument("--dome", type=float, default=1.6)
    parser.add_argument("--spacing", type=float, default=1.6)
    parser.add_argument("--step", type=float, default=0.1)
    parser.add_argument("--curvature", type=float, default=0.0044)
    args = parser.parse_args()

    outline = flatten(read_path_data(args.source), args.step)
    vertices, triangles = build(outline, args.size, args.thickness / 2, args.radius, args.dome, args.spacing)
    vertices = bend(vertices, args.curvature)
    write_binobj(args.output, vertices, triangles)
    print(f"{args.output}: {len(vertices)} vertices, {len(triangles)} triangles")


if __name__ == "__main__":
    main()
