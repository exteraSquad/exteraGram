#!/usr/bin/env python3

# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import argparse
import ast
import py_compile
import re
import shutil
import stat
import sys
import tempfile
import zipfile
from pathlib import Path


SDK_ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIR = SDK_ROOT / "src"
VERSION_MODULE = "_sdk_version.py"
TARGET_PYTHON = "3.11"
ASSETS_DIR = "plugins_pysdk"
ARCHIVE_PATTERN = "sdk-{abi}.zip"
VERSION_FILE = "v.txt"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
CHANNELS = ("stable", "beta")
IGNORED_DIRS = {"__pycache__"}
IGNORED_NAMES = {".DS_Store"}
IGNORED_SUFFIXES = {".pyc", ".pyo"}
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
ZIP_UNIX = 3


def log(message):
    print(f"[Plugins-PySDK]: {message}", flush=True)


def fail(message):
    raise SystemExit(f"[Plugins-PySDK]: {message}")


def check_interpreter(target):
    current = f"{sys.version_info.major}.{sys.version_info.minor}"
    if current != target:
        fail(
            f"the SDK is shipped as bytecode for the Python {target} the app embeds, "
            f"but this is Python {current} ({sys.executable}). "
            f"Run the build with Python {target}: set python.build in local.properties or BUILD_PYTHON."
        )


def find_assignment(tree, name):
    nodes = [
        node for node in tree.body
        if isinstance(node, ast.Assign)
        and len(node.targets) == 1
        and isinstance(node.targets[0], ast.Name)
        and node.targets[0].id == name
    ]
    if len(nodes) != 1:
        fail(f"{VERSION_MODULE} must assign {name} exactly once at module level")
    return nodes[0].value


def read_version_module(path):
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source, filename=str(path))

    version = find_assignment(tree, "__version__")
    if not isinstance(version, ast.Constant) or not isinstance(version.value, str) \
            or not re.fullmatch(r"\d+(\.\d+)*", version.value):
        fail(f"{VERSION_MODULE}: __version__ must be a dotted number string")

    parts = find_assignment(tree, "version")
    try:
        parts = ast.literal_eval(parts)
    except ValueError:
        parts = None
    if parts != tuple(int(part) for part in version.value.split(".")):
        fail(f"{VERSION_MODULE}: version tuple does not match __version__ {version.value}")

    beta = find_assignment(tree, "__beta__")
    if not isinstance(beta, ast.Constant) or not isinstance(beta.value, bool) \
            or beta.lineno != beta.end_lineno:
        fail(f"{VERSION_MODULE}: __beta__ must be a True/False literal")

    return source, version.value, beta


def stamp_channel(source, beta_node, is_beta):
    lines = source.splitlines(keepends=True)
    line = lines[beta_node.lineno - 1].encode("utf-8")
    line = line[:beta_node.col_offset] + repr(is_beta).encode("ascii") + line[beta_node.end_col_offset:]
    lines[beta_node.lineno - 1] = line.decode("utf-8")
    return "".join(lines)


def list_sources():
    if not SOURCE_DIR.is_dir():
        fail(f"SDK sources not found at {SOURCE_DIR}")
    files = []
    for path in SOURCE_DIR.rglob("*"):
        relative = path.relative_to(SOURCE_DIR)
        if not path.is_file() or IGNORED_DIRS.intersection(relative.parts[:-1]):
            continue
        if path.name in IGNORED_NAMES or path.suffix in IGNORED_SUFFIXES:
            continue
        files.append(relative)
    if not files:
        fail(f"no SDK sources in {SOURCE_DIR}")
    return sorted(files, key=lambda relative: relative.as_posix())


def compile_module(source_path, relative, staging_dir):
    target = staging_dir / relative.with_suffix(".pyc")
    target.parent.mkdir(parents=True, exist_ok=True)
    try:
        py_compile.compile(
            str(source_path),
            cfile=str(target),
            dfile=relative.as_posix(),
            doraise=True,
            optimize=2,
            invalidation_mode=py_compile.PycInvalidationMode.UNCHECKED_HASH,
        )
    except py_compile.PyCompileError as error:
        fail(f"could not compile {relative.as_posix()}: {error.msg.strip()}")


def stage(staging_dir, work_dir, version_source):
    for relative in list_sources():
        source_path = SOURCE_DIR / relative
        if relative.suffix != ".py":
            target = staging_dir / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source_path, target)
            continue
        if relative.as_posix() == VERSION_MODULE:
            source_path = work_dir / VERSION_MODULE
            source_path.write_text(version_source, encoding="utf-8")
        compile_module(source_path, relative, staging_dir)


def write_entry(archive, name, data, mode):
    info = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
    info.create_system = ZIP_UNIX
    info.external_attr = mode << 16
    if name.endswith("/"):
        info.external_attr |= 0x10
    archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def write_archive(staging_dir, archive_path):
    paths = sorted(staging_dir.rglob("*"), key=lambda path: path.relative_to(staging_dir).as_posix())
    with zipfile.ZipFile(archive_path, "w") as archive:
        for path in paths:
            name = path.relative_to(staging_dir).as_posix()
            if path.is_dir():
                write_entry(archive, name + "/", b"", stat.S_IFDIR | 0o755)
            else:
                write_entry(archive, name, path.read_bytes(), stat.S_IFREG | 0o644)


def install(archive_path, out_dir, abis, version):
    target_dir = out_dir / ASSETS_DIR
    if target_dir.exists():
        shutil.rmtree(target_dir)
    target_dir.mkdir(parents=True)
    for abi in abis:
        shutil.copyfile(archive_path, target_dir / ARCHIVE_PATTERN.format(abi=abi))
    (target_dir / VERSION_FILE).write_text(version, encoding="utf-8")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Compile the plugins SDK to bytecode and pack the archives the app ships in its assets."
    )
    parser.add_argument("--out", required=True, type=Path,
                        help=f"directory that receives {ASSETS_DIR}/")
    parser.add_argument("--channel", required=True, choices=CHANNELS,
                        help="release channel stamped into the SDK")
    parser.add_argument("--abi", required=True, action="append", choices=ABIS, dest="abis",
                        help="ABI to pack an archive for; repeat for several")
    parser.add_argument("--python", default=TARGET_PYTHON, metavar="MAJOR.MINOR",
                        help=f"Python version embedded in the app (default: {TARGET_PYTHON})")
    return parser.parse_args()


def main():
    args = parse_args()
    check_interpreter(args.python)

    is_beta = args.channel == "beta"
    abis = list(dict.fromkeys(args.abis))
    source, version, beta_node = read_version_module(SOURCE_DIR / VERSION_MODULE)

    with tempfile.TemporaryDirectory(prefix="plugins-pysdk-build-") as temporary:
        work_dir = Path(temporary)
        staging_dir = work_dir / "sdk"
        staging_dir.mkdir()
        stage(staging_dir, work_dir, stamp_channel(source, beta_node, is_beta))
        (staging_dir / VERSION_FILE).write_text(f"{version}|{int(is_beta)}", encoding="utf-8")

        archive_path = work_dir / "sdk.zip"
        write_archive(staging_dir, archive_path)
        install(archive_path, args.out.resolve(), abis, version)

    log(f"built SDK {version} ({args.channel}) for {', '.join(abis)}")


if __name__ == "__main__":
    main()
