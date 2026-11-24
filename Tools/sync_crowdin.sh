#!/usr/bin/env bash

set -euo pipefail

if [[ -z "${CROWDIN_PERSONAL_TOKEN:-}" ]]; then
    echo "CROWDIN_PERSONAL_TOKEN is not set" >&2
    exit 1
fi

repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
crowdin_npm_cache="${TMPDIR:-/tmp}/exteragram-crowdin-npm-cache"
resources_directory="$repository_root/TMessagesProj/src/main/res"
source_file="$resources_directory/values/extera.xml"
minimum_completion_percent="${CROWDIN_MIN_COMPLETION_PERCENT:-25}"

mkdir -p "$crowdin_npm_cache"
cd "$repository_root"

if ! [[ "$minimum_completion_percent" =~ ^[0-9]+$ ]] ||
    (( 10#$minimum_completion_percent > 100 )); then
    echo "CROWDIN_MIN_COMPLETION_PERCENT must be an integer from 0 to 100" >&2
    exit 1
fi

minimum_completion_percent=$((10#$minimum_completion_percent))
dry_run=false

for argument in "$@"; do
    if [[ "$argument" == "--dryrun" ]]; then
        dry_run=true
        break
    fi
done

current_branch="$(git rev-parse --abbrev-ref HEAD)"

if [[ "$current_branch" != "main" ]]; then
    echo "Skipping source upload: on $current_branch, not main" >&2
elif ! git diff --quiet HEAD -- "$source_file"; then
    echo "Skipping source upload: $source_file has uncommitted changes" >&2
else
    source_command=(
        upload sources
        --config crowdin.yml
        --no-progress
    )

    if "$dry_run"; then
        source_command+=(--dryrun)
    fi

    npm_config_cache="$crowdin_npm_cache" \
        npx --yes @crowdin/cli@4 "${source_command[@]}"
fi

upload_command=(
    upload translations
    --config crowdin.yml
    --no-progress
    --language ru
    --auto-approve-imported
)

if "$dry_run"; then
    upload_command+=(--dryrun)
fi

npm_config_cache="$crowdin_npm_cache" \
    npx --yes @crowdin/cli@4 "${upload_command[@]}"

baseline_directory="$(git rev-parse --path-format=absolute --git-path crowdin-baseline)"
edits_directory="$(mktemp -d)"
edits_resources_directory="$edits_directory/${resources_directory#"$repository_root"/}"
trap 'rm -rf -- "$edits_directory"' EXIT

if [[ "$current_branch" != "main" ]]; then
    echo "Skipping translation edits upload: on $current_branch, not main" >&2
elif [[ ! -f "$baseline_directory/commit" ]]; then
    echo "Skipping translation edits upload: no baseline yet, this sync records one" >&2
elif ! git merge-base --is-ancestor "$(<"$baseline_directory/commit")" HEAD; then
    echo "Skipping translation edits upload: HEAD is behind the last sync" >&2
else
    find "$resources_directory" -mindepth 2 -maxdepth 2 -type f \
        -path '*/values-*/extera.xml' ! -path '*/values-ru/*' \
        -exec python3 "$repository_root/Tools/collect_crowdin_edits.py" \
            --source "$source_file" \
            --baseline "$baseline_directory" \
            --output "$edits_resources_directory" \
            {} +

    if [[ -d "$edits_resources_directory" ]]; then
        mkdir -p "$edits_resources_directory/values"
        cp -- "$source_file" "$edits_resources_directory/values/"

        edits_command=(
            upload translations
            --config crowdin.yml
            --base-path "$edits_directory"
            --no-progress
            --plain
            --auto-approve-imported
            --import-eq-suggestions
        )

        if "$dry_run"; then
            edits_command+=(--dryrun)
        fi

        npm_config_cache="$crowdin_npm_cache" \
            npx --yes @crowdin/cli@4 "${edits_command[@]}"
    fi
fi

download_marker="$(mktemp)"
trap 'rm -rf -- "$edits_directory" "$download_marker"' EXIT

npm_config_cache="$crowdin_npm_cache" \
    npx --yes @crowdin/cli@4 download translations \
    --config crowdin.yml \
    --no-progress \
    --exclude-language en \
    --exclude-language ru \
    "$@"

if "$dry_run"; then
    exit 0
fi

find "$resources_directory" -mindepth 2 -maxdepth 2 -type f \
    -path '*/values-*/extera.xml' -newer "$download_marker" \
    -exec python3 "$repository_root/Tools/cleanup_crowdin_translations.py" \
        --source "$source_file" \
        --minimum-completion "$minimum_completion_percent" \
        {} +

if [[ "$current_branch" == "main" ]]; then
    mkdir -p "$baseline_directory"
    git rev-parse HEAD > "$baseline_directory/commit"

    find "$resources_directory" -mindepth 2 -maxdepth 2 -type f \
        -path '*/values-*/extera.xml' -newer "$download_marker" -print0 |
        while IFS= read -r -d '' translation_file; do
            locale_directory="$baseline_directory/$(basename -- "$(dirname -- "$translation_file")")"
            mkdir -p "$locale_directory"
            cp -- "$translation_file" "$locale_directory/"
        done
fi
