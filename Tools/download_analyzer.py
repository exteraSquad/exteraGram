import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile


REPO = "exteraSquad/plugins-pysdk-builds"
API_RELEASES_URL = f"https://api.github.com/repos/{REPO}/releases"
PUBLIC_RELEASES_URL = f"https://github.com/{REPO}/releases"
NETWORK_TIMEOUT_SECONDS = 30
NETWORK_RETRIES = 3
NETWORK_RETRY_DELAY_SECONDS = 2
ARCHIVE = "analyzer.zip"
LIBRARY = "libextera_analyzer.so"
TEXTS = "texts.json"
RELEASE_ASSET = "analyzer.release"
MACHINES = {"arm64-v8a": (183, 2), "armeabi-v7a": (40, 1)}
CACHE_ERRORS = (OSError, ValueError, KeyError, TypeError, AttributeError, RuntimeError, NotImplementedError, zipfile.BadZipFile)


class EmptyReleaseListError(RuntimeError):
    pass


def log(message):
    print(f"[Plugin-Analyzer-Downloader]: {message}", flush=True)


def fail(message):
    raise SystemExit(f"[Plugin-Analyzer-Downloader]: {message}")


def get_temp_path():
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "temp"))


def is_retryable_error(error):
    if isinstance(error, urllib.error.HTTPError):
        return error.code == 429 or 500 <= error.code < 600
    return isinstance(error, (urllib.error.URLError, TimeoutError, ConnectionError, EmptyReleaseListError))


def is_rate_limit_error(error):
    if not isinstance(error, urllib.error.HTTPError) or error.code not in (403, 429):
        return False
    headers = error.headers
    if headers is not None:
        remaining = headers.get("X-RateLimit-Remaining")
        if remaining is not None and remaining.strip() == "0":
            return True
        if headers.get("Retry-After"):
            return True
    reason = error.reason if isinstance(error.reason, str) else ""
    return "rate limit" in reason.lower()


def get_local_property(name):
    properties = Path(__file__).resolve().parent.parent / "local.properties"
    try:
        lines = properties.read_text(encoding="utf-8").splitlines()
    except FileNotFoundError:
        return ""
    value = ""
    for line in lines:
        match = re.match(r"^\s*" + re.escape(name) + r"\s*[=:]\s*(.*?)\s*$", line)
        if match:
            value = match.group(1)
    return value


def get_github_token():
    token = os.environ.get("GH_TOKEN", "").strip() or os.environ.get("GITHUB_TOKEN", "").strip()
    return token or get_local_property("github.token")


def open_url(request):
    url = urllib.parse.urlsplit(request.full_url)
    if url.scheme == "https" and url.netloc.lower() in ("api.github.com", "api.github.com:443"):
        token = get_github_token()
        if token:
            request.add_unredirected_header("Authorization", f"Bearer {token}")
    last_error = None
    for attempt in range(1, NETWORK_RETRIES + 1):
        try:
            return urllib.request.urlopen(request, timeout=NETWORK_TIMEOUT_SECONDS)
        except Exception as e:
            if not is_retryable_error(e) or attempt == NETWORK_RETRIES:
                raise

            last_error = e
            log(
                f"network request failed ({e}); "
                f"retrying {attempt}/{NETWORK_RETRIES - 1}"
            )
            time.sleep(NETWORK_RETRY_DELAY_SECONDS)

    raise last_error


def request_json(url):
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "extera-pysdk-build-helper",
        },
    )
    with open_url(request) as response:
        return json.load(response)


def download_file(url, output_path):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "extera-pysdk-build-helper"},
    )
    with open_url(request) as response, open(output_path, "wb") as fw:
        shutil.copyfileobj(response, fw)


def get_sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as fr:
        for chunk in iter(lambda: fr.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_text_url(url):
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "extera-pysdk-build-helper"},
    )
    with open_url(request) as response:
        return response.read().decode("utf-8").strip()


def parse_sha256(text):
    match = re.search(r"\b([0-9a-fA-F]{64})\b", text)
    if not match:
        fail("could not parse remote sha256")
    return match.group(1).lower()


def request_releases(page):
    url = f"{API_RELEASES_URL}?per_page=30&page={page}"
    for attempt in range(1, NETWORK_RETRIES + 1):
        releases = request_json(url)
        if releases or page > 1:
            return releases
        if attempt < NETWORK_RETRIES:
            log(f"GitHub returned an empty release list; retrying {attempt}/{NETWORK_RETRIES - 1}")
            time.sleep(NETWORK_RETRY_DELAY_SECONDS)
    raise EmptyReleaseListError(f"GitHub returned an empty release list for {PUBLIC_RELEASES_URL}")


def request_all_releases():
    releases = []
    page = 1
    while True:
        batch = request_releases(page)
        if not batch:
            break
        releases.extend(batch)
        page += 1
    return sorted(releases, key=lambda release: release.get("published_at") or "", reverse=True)


def get_asset(release, name):
    for asset in release.get("assets", []):
        if asset.get("name") == name:
            return asset
    fail(
        f"release {release.get('tag_name', 'unknown')} does not contain asset {name}; "
        f"check {release.get('html_url', PUBLIC_RELEASES_URL)}"
    )


def find_latest_release(allow_beta):
    for release in request_all_releases():
        if release.get("draft"):
            continue
        is_beta = bool(release.get("prerelease")) or "beta" in (release.get("tag_name") or "").lower()
        if is_beta and not allow_beta:
            continue
        return release, is_beta

    fail(f"no public release found at {PUBLIC_RELEASES_URL}")


def get_remote_sha256(asset):
    digest = asset.get("digest")
    if digest and digest.startswith("sha256:"):
        return digest.removeprefix("sha256:").lower()

    sha_asset_url = asset["browser_download_url"] + ".sha256"
    try:
        return parse_sha256(read_text_url(sha_asset_url))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def is_current_file(path, expected_sha256):
    return path.exists() and expected_sha256 and get_sha256(path) == expected_sha256


def sync_asset(asset, output_path, expected_sha256):
    if is_current_file(output_path, expected_sha256):
        log(f"{output_path.name} is up to date")
        return

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="analyzer-download-") as temp_dir:
        temp_path = Path(temp_dir) / output_path.name
        log(f"downloading {asset['browser_download_url']}")
        download_file(asset["browser_download_url"], temp_path)

        actual_sha256 = get_sha256(temp_path)
        if expected_sha256 and actual_sha256 != expected_sha256:
            fail(
                f"sha256 mismatch for {asset['name']}: "
                f"expected {expected_sha256}, got {actual_sha256}"
            )

        shutil.move(str(temp_path), output_path)
        log(f"installed {output_path.name}")


def install_staged_dir(staging_dir, target_dir):
    target_dir.parent.mkdir(parents=True, exist_ok=True)
    if target_dir.exists():
        shutil.rmtree(target_dir.absolute())
    shutil.move(str(staging_dir), target_dir)


def validate(path, abis):
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        allowed = {f"{abi}/{LIBRARY}" for abi in MACHINES} | {TEXTS}
        if not names or set(names) - allowed or len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError(f"analyzer archive must contain only <abi>/{LIBRARY} and {TEXTS}")
        if TEXTS in names:
            texts = json.loads(archive.read(TEXTS).decode("utf-8"))
            if not isinstance(texts.get("statements"), dict) or not isinstance(texts.get("locales"), list):
                raise ValueError(f"malformed {TEXTS}")
        for abi in abis:
            if f"{abi}/{LIBRARY}" not in names:
                raise ValueError(f"analyzer archive has no {abi} library")
        for name in names:
            if name == TEXTS:
                continue
            machine, bits = MACHINES[name.split("/")[0]]
            with archive.open(name) as library:
                header = library.read(20)
            if (len(header) != 20 or header[:4] != b"\x7fELF" or header[4] != bits
                    or header[5] != 1 or int.from_bytes(header[16:18], "little") != 3
                    or int.from_bytes(header[18:20], "little") != machine):
                raise ValueError(f"wrong architecture for {name}")


def unpack(archive_path, libs_dir, assets_dir, abis, release):
    with tempfile.TemporaryDirectory(prefix="analyzer-unpack-") as temporary:
        libs = Path(temporary) / "jniLibs"
        assets = Path(temporary) / "assets"
        build = hashlib.sha256()
        with zipfile.ZipFile(archive_path) as archive:
            for abi in abis:
                data = archive.read(f"{abi}/{LIBRARY}")
                (libs / abi).mkdir(parents=True)
                (libs / abi / LIBRARY).write_bytes(data)
                build.update(abi.encode() + b"\0" + hashlib.sha256(data).digest())
            texts = archive.read(TEXTS) if TEXTS in archive.namelist() else None
        (assets / "plugin_analyzer").mkdir(parents=True)
        (assets / "plugin_analyzer" / "build_id").write_text(build.hexdigest()[:16], encoding="utf-8")
        if texts is not None:
            (assets / "plugin_analyzer" / TEXTS).write_bytes(texts)
        if release is not None:
            (assets / "plugin_analyzer" / "release").write_text(str(release), encoding="ascii")
        install_staged_dir(libs, libs_dir)
        install_staged_dir(assets, assets_dir)


def cache_dir():
    return Path(get_temp_path()) / "analyzer"


def from_cache(abis, allow_beta):
    manifests = sorted(cache_dir().glob("*.json"), key=lambda path: path.stat().st_mtime_ns, reverse=True)
    beta_manifests = []
    for path in manifests:
        try:
            manifest = json.loads(path.read_text(encoding="utf-8"))
            if (manifest.get("version") != 2 or manifest.get("repo") != REPO
                    or type(manifest.get("beta")) is not bool):
                raise ValueError("invalid cache manifest")
            if manifest["beta"] and not allow_beta:
                beta_manifests.append((path, manifest))
                continue
            return use_cached(path, manifest, abis)
        except CACHE_ERRORS as error:
            log(f"skipping unusable cache {path.name}: {error}")
    for path, manifest in beta_manifests:
        try:
            archive, _ = use_cached(path, manifest, abis)
            return archive, None
        except CACHE_ERRORS as error:
            log(f"skipping unusable cache {path.name}: {error}")
    fail(
        "no verified analyzer cache for "
        + ", ".join(abis)
        + f". Download once from {PUBLIC_RELEASES_URL} (set GH_TOKEN, GITHUB_TOKEN or github.token in local.properties "
        "if GitHub is rate-limiting), or point extera.analyzer.dir in local.properties to a local build."
    )


def use_cached(path, manifest, abis):
    archive = path.with_suffix(".zip")
    if not is_current_file(archive, path.stem):
        raise ValueError("SHA-256 mismatch")
    validate(archive, abis)
    log(f"using cached analyzer from {manifest.get('tag', path.stem)}")
    release = manifest.get("analyzer_release")
    if release is not None and (type(release) is not int or not 0 < release <= 2**63 - 1):
        raise ValueError("invalid cached analyzer release number")
    return archive, release


def has_archive(release):
    return ARCHIVE in {asset.get("name") for asset in release.get("assets", [])}


def find_release(allow_beta):
    release, is_beta = find_latest_release(allow_beta)
    if has_archive(release):
        return release, is_beta
    log(f"release {release.get('tag_name', 'unknown')} has no {ARCHIVE}; looking for the newest release that has it")
    beta_fallback = None
    for candidate in request_all_releases():
        if candidate.get("draft") or not has_archive(candidate):
            continue
        tag = candidate.get("tag_name") or ""
        candidate_is_beta = bool(candidate.get("prerelease")) or "beta" in tag.lower()
        if allow_beta or not candidate_is_beta:
            return candidate, candidate_is_beta
        if beta_fallback is None:
            beta_fallback = candidate
    if beta_fallback is not None:
        return beta_fallback, True
    fail(
        f"no release at {PUBLIC_RELEASES_URL} has {ARCHIVE}. "
        f"Point extera.analyzer.dir in local.properties (or EXTERA_ANALYZER_DIR) to a directory with {ARCHIVE} "
        "built by rust/tools/build_android.py in plugins-pysdk."
    )


def from_release(abis, allow_beta):
    release, is_beta = find_release(allow_beta)
    asset = get_asset(release, ARCHIVE)
    digest = get_remote_sha256(asset)
    if not digest:
        raise ValueError(f"SHA-256 is missing for {ARCHIVE}")
    archive = cache_dir() / f"{digest}.zip"
    sync_asset(asset, archive, digest)
    validate(archive, abis)
    release_number = None
    release_asset = next((asset for asset in release.get("assets", []) if asset.get("name") == RELEASE_ASSET), None)
    if release_asset is not None:
        release_digest = get_remote_sha256(release_asset)
        if not release_digest:
            raise ValueError(f"SHA-256 is missing for {RELEASE_ASSET}")
        release_path = cache_dir() / f"{release_digest}.release"
        sync_asset(release_asset, release_path, release_digest)
        release_number = read_release_number(release_path)
    manifest = {
        "version": 2, "repo": REPO, "tag": release.get("tag_name"), "beta": is_beta,
        "analyzer_release": release_number,
    }
    archive.with_suffix(".json").write_text(json.dumps(manifest), encoding="utf-8")
    log(f"using analyzer from {release.get('tag_name', 'GitHub release')}")
    return archive, release_number if allow_beta or not is_beta else None


def read_release_number(path):
    with path.open("rb") as stream:
        raw = stream.read(65)
    text = raw.decode("ascii").strip()
    if len(raw) > 64 or not text.isdecimal() or not 0 < int(text) <= 2**63 - 1:
        raise ValueError("invalid analyzer release number")
    return int(text)


def fetch(abis, allow_beta):
    local = os.environ.get("EXTERA_ANALYZER_DIR", "").strip() or get_local_property("extera.analyzer.dir")
    if local:
        archive = Path(local) / ARCHIVE
        validate(archive, abis)
        log(f"using local analyzer {archive}")
        release_path = Path(local) / RELEASE_ASSET
        return archive, read_release_number(release_path) if release_path.is_file() else None
    try:
        log("looking for latest analyzer assets")
        return from_release(abis, allow_beta)
    except Exception as error:
        if not is_retryable_error(error) and not is_rate_limit_error(error):
            raise
        log(f"could not reach GitHub analyzer assets ({error}); using the cache")
        return from_cache(abis, allow_beta)


def main():
    if len(sys.argv) != 5:
        raise SystemExit("usage: download_analyzer.py <jniLibs dir> <assets dir> <allow-beta (1/0)> <ABI[,ABI...]>")
    libs_dir, assets_dir, beta, abi_text = sys.argv[1:]
    requested = list(dict.fromkeys(abi.strip() for abi in abi_text.split(",") if abi.strip()))
    abis = [abi for abi in requested if abi in MACHINES]
    if not abis:
        log(f"no analyzer for {', '.join(requested) or 'this variant'}; it is left out")
        for directory in (Path(libs_dir), Path(assets_dir)):
            shutil.rmtree(directory, ignore_errors=True)
            directory.mkdir(parents=True)
        return
    archive, release = fetch(abis, beta == "1")
    unpack(archive, Path(libs_dir), Path(assets_dir), abis, release)


if __name__ == "__main__":
    main()
