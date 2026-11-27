# Plugins SDK

`plugins-pysdk/` is the Python SDK that ships inside every `full` build and is also delivered over the
air. It is a published API: plugin authors import it, and installs of old and new versions coexist in
the wild. Treat every public name as a compatibility promise.

## Layout

| Path | Purpose |
| --- | --- |
| `plugins-pysdk/src/` | Runtime modules imported by plugins by top-level name (`base_plugin`, `hook_utils`, `client_utils`, `ui`, `file_utils`, `intents`, `log_utils`, `markdown_utils`, `plugin_settings`, `android_utils`, `dev_server`) |
| `plugins-pysdk/src/elyxcore/` | ElyxEngine, the Python implementation of the Kotlin `PluginsEngine` interface |
| `plugins-pysdk/src/extera_utils/` | Helpers: class proxying, metadata parsing, text formatting, lifecycle, caller lookup |
| `plugins-pysdk/src/_sdk_version.py` | Version, beta flag, safe-mode importer and startup hooks |
| `plugins-pysdk/tests/` | Host unit tests with stubbed Java packages |
| `plugins-pysdk/examples/` | Sample plugins (e.g. `partial_hooks_demo.plugin`) |
| `plugins-pysdk/tools/` | `build_sdk.py`, `sign-sdk.sh`, `sign-artifact.sh`, `build-stubs-package.sh` |
| Java side (`src/full/…/plugins/`) | `PluginsController`, `PythonPluginsEngine`, hooks, analyzer, catalog, plugin UI |

## Packaging

Every `full` variant gets a `buildPythonSdk<Variant>` task that runs `plugins-pysdk/tools/build_sdk.py`
and adds the result to that variant's assets:

```
plugins_pysdk/v.txt            SDK version
plugins_pysdk/sdk-<abi>.zip    one archive per ABI of the variant
```

- The archives contain sourceless `.pyc` modules compiled with `-OO` (no docstrings, no asserts) plus a
  `v.txt` holding `<version>|<1 for beta, 0 for stable>`.
- On first start the app unpacks the archive into its files directory and puts that directory on
  `sys.path`.
- Bytecode is tied to the interpreter minor version, so the build must run on Python 3.11 — the same
  version Chaquopy embeds. `build_sdk.py` fails on anything else. Python comes from `python.build` in
  `local.properties`, `BUILD_PYTHON`, or `python3.11` / `py -3.11` on `PATH`.
- Manual build:
  ```bash
  python3.11 plugins-pysdk/tools/build_sdk.py --out plugins-pysdk/build/ota \
      --channel beta --abi arm64-v8a --abi armeabi-v7a
  ```
- The archives are identical for every ABI; they are named per ABI because the update channel addresses
  them that way.

## Versioning

`src/_sdk_version.py` holds the version twice:

```python
__version__ = "1.4.6.5"
version = (1, 4, 6, 5)
```

- The build fails when the two disagree. Keep them identical.
- Raise the version with **every** change that ships in the SDK. An over-the-air update is only applied
  over the APK copy when it carries a newer version (or the same version when the update is beta), so an
  unchanged version silently loses the update.
- The channel is not stored in the tree. `build_sdk.py --channel` writes `__beta__` into the packaged
  module and into `v.txt`; Gradle passes `beta` for the `beta` flavor and `stable` otherwise. The
  `__beta__ = False` literal in the source only applies when modules are imported straight from `src/`,
  as the tests and the stub generator do.
- The SDK refuses to initialize on app builds older than exteraGram 12.5.1 (`BuildVars.BUILD_VERSION`
  gate in `_sdk_version.py`) and tells the user to update. Do not remove that gate casually; it protects
  old clients from a newer SDK.

## The Kotlin ↔ Python contract

`src/elyxcore/_plugin_engine.py` implements the Kotlin `PluginsController.PluginsEngine` interface
through Chaquopy `@joverride`. Neither compiler sees the other side, so a mismatch is a runtime failure:

- A method added to `PluginsEngine`, `Plugin` or `PluginInstallParams` in `PluginsController.kt` must be
  implemented in `_plugin_engine.py` in the same change.
- The same applies in reverse: Python code must not call a Kotlin method that only exists in the full
  flavor unless it also exists in the lite stub. Lite and full `PluginsController` signatures stay
  identical (see `AGENTS.md`, "Full and lite flavors").
- Names are bound by string. Renaming a Kotlin method that Python calls, or a `setting`/`menu` key that
  crosses the boundary, breaks plugins without a compile error.
- `_sdk_version.py` hooks app internals by method name (`setup_hooks`, `SafeModeImporter`). When an
  internal method it hooks changes name or signature, update the hook and its version gate together.

## Building a change

1. Edit `plugins-pysdk/src/**`.
2. Raise `_sdk_version.py`.
3. Run the tests: `python3.11 -m unittest discover -s plugins-pysdk/tests`.
4. If the change adds or changes a public API, check that plugin-facing docs/examples still match
   (`examples/`, `README.md`).
5. Build a `full` variant to prove the SDK is packaged and imports at startup.

Rules:

- Do not rename or remove a module, class or function plugins can import. Add, deprecate, or version.
- Keep the SDK importable on plain CPython 3.11: it is imported on the host by the tests and the stub
  generator, with Java packages stubbed.
- Keep `import` of Java packages inside functions where the module must also work on the host.
- No new third-party dependency without an explicit request: the runtime packages are pinned in
  `TMessagesProj/gradle/extera-python.gradle` (`beautifulsoup4`, `debugpy`, `lxml`, `packaging`,
  `pillow`, `requests`, `PyYAML`) and every addition grows the APK for all users.
- Keep `#` license headers on new modules and tests.

## Over-the-air updates

Besides the APK copy, the app installs SDK updates posted to its update channel. They run with the
app's rights, so an update is accepted only with a valid signature.

- Signed payload: `extera-pysdk|<version>|<abi>|<sha256 of the archive>`.
- Signature: P-256. The public key is pinned in
  `TMessagesProj/src/full/java/com/exteragram/messenger/plugins/utils/SdkSignature.kt`; the private key
  never enters this repository.
- Signing:
  ```bash
  SDK_SIGNING_KEY_FILE=/path/to/sdk-signing-key.pem \
    bash plugins-pysdk/tools/sign-sdk.sh plugins-pysdk/build/ota/plugins_pysdk/sdk-arm64-v8a.zip \
    1.4.6.5 arm64-v8a beta
  ```
  It verifies that the version and channel on the command line match the archive, and writes
  `<archive>.sig` and `<archive>.message.txt`. The message can carry `app_version>=13.2.2` or
  `app_version_code` to limit the update to matching builds; an update without a `signature = ...` line
  is ignored.
- Key rotation means generating a new pair, shipping the new public key in an app release, and keeping
  the old key until every user is past that release. Clients pin exactly one key. Never rotate, weaken
  or bypass this check as part of an unrelated change.

## Type stubs

Plugin authors get editor completion from the `extera-pysdk-stubs` PyPI package, generated by
`plugins-pysdk/tools/build-stubs-package.sh` (needs `mypy` and `build`). The `.pyi` files and the
packaging metadata are generated from the modules, so the stub signatures must stay in sync with the
runtime signatures; a wrong stub is a bug report waiting to happen.

## Analyzer

The plugin analyzer is a prebuilt native component downloaded on the first `full` build by
`Tools/download_analyzer.py`, cached under `Tools/temp`, and refreshed by the in-app updater. It is not
built from this repository. Do not vendor it, do not add its binaries to git, and do not change the
download source or its verification as a side effect of an SDK change.

## Review checklist for an SDK change

- [ ] version raised in `_sdk_version.py` (both places)
- [ ] tests pass on Python 3.11
- [ ] Kotlin side updated in the same change if the engine contract moved, including the lite stub
- [ ] public Python names unchanged, or a migration path exists
- [ ] no new runtime dependency
- [ ] `full` build packages and starts the SDK
- [ ] no key material, signed artifact or `plugins-pysdk/build` output committed
