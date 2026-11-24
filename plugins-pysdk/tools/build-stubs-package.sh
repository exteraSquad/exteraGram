#!/usr/bin/env bash

# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

set -euo pipefail

log() {
    echo "build-stubs-package: $*" >&2
}

fail() {
    log "$*"
    exit 1
}

: "${STUBS_PACKAGE_VERSION:?}"

sdk_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SOURCE_DIR="${SOURCE_DIR:-$sdk_root/src}"
SDK_CHANNEL="${SDK_CHANNEL:-stable}"
STUBS_PACKAGE_NAME="${STUBS_PACKAGE_NAME:-extera-pysdk-stubs}"
STUBS_IMPORT_PACKAGE="${STUBS_IMPORT_PACKAGE:-extera_pysdk_stubs}"
OUT_DIR="${OUT_DIR:-$sdk_root/build/stubs-pypi}"
STUBS_ARCHIVE_DIR="${STUBS_ARCHIVE_DIR:-$sdk_root/build/stubs-files}"
STUBS_ARCHIVE_NAME="${STUBS_ARCHIVE_NAME:-pyi-files.zip}"

PYPI_VERSION="$STUBS_PACKAGE_VERSION"
if [[ "$SDK_CHANNEL" == "beta" && "$PYPI_VERSION" != *[aAbBcC][0-9]* ]]; then
    PYPI_VERSION="${PYPI_VERSION}b.post${GITHUB_RUN_NUMBER:-0}"
fi

work_dir="$(mktemp -d)"
raw_stubs_dir="$work_dir/raw-stubs"
project_dir="$work_dir/project"
package_dir="$project_dir/$STUBS_IMPORT_PACKAGE"

cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

[[ -d "$SOURCE_DIR" ]] || fail "source directory does not exist: $SOURCE_DIR"

log "generating stubs from $SOURCE_DIR"
stubgen "$SOURCE_DIR" -o "$raw_stubs_dir"

stub_root="$raw_stubs_dir"
if [[ -d "$raw_stubs_dir/$(basename "$SOURCE_DIR")" ]]; then
    stub_root="$raw_stubs_dir/$(basename "$SOURCE_DIR")"
fi

mkdir -p "$package_dir"
cp -a "$stub_root"/. "$package_dir"/

rm -rf "$STUBS_ARCHIVE_DIR"
mkdir -p "$STUBS_ARCHIVE_DIR"
stubs_archive="$(cd "$STUBS_ARCHIVE_DIR" && pwd)/$STUBS_ARCHIVE_NAME"
log "packing raw .pyi files into $stubs_archive"
(
    cd "$stub_root"
    find . -type f -name "*.pyi" -print | zip -q "$stubs_archive" -@
)

find "$package_dir" -type d -exec touch "{}/__init__.py" \;
touch "$package_dir/py.typed"

cat > "$project_dir/pyproject.toml" <<EOF
[build-system]
requires = ["setuptools>=69", "wheel"]
build-backend = "setuptools.build_meta"

[project]
name = "$STUBS_PACKAGE_NAME"
version = "$PYPI_VERSION"
description = "Type stubs for Extera Python SDK"
requires-python = ">=3.11"
classifiers = [
    "Typing :: Stubs Only",
]

[tool.setuptools.packages.find]
where = ["."]
include = ["$STUBS_IMPORT_PACKAGE*"]

[tool.setuptools.package-data]
"*" = ["*.pyi", "py.typed"]
EOF

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
log "building $STUBS_PACKAGE_NAME $PYPI_VERSION"
python -m build "$project_dir" --outdir "$OUT_DIR"

log "built stubs package into $OUT_DIR"
