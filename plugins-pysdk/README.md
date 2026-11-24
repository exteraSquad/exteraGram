# exteraGram plugins SDK

The Python SDK for exteraGram plugins: the modules a plugin imports (`base_plugin`, `hook_utils`,
`client_utils`, `ui`, ...) and ElyxCore, the engine behind `.elyx` plugins. The `full` flavor of the
app ships it next to the embedded Python runtime, and it is built from this directory on every app
build.

| Path                           | Purpose                                                            |
|--------------------------------|--------------------------------------------------------------------|
| `src/`                         | Runtime modules; plugins import them by their top-level names      |
| `src/elyxcore/`                | ElyxEngine, the Python implementation of `PluginsEngine`           |
| `tests/`                       | Unit tests that run on the host with stubbed Java packages         |
| `examples/`                    | Sample plugins                                                     |
| `tools/build_sdk.py`           | Builds the archives the app ships                                  |
| `tools/sign-sdk.sh`            | Signs an archive for an over-the-air update                        |
| `tools/build-stubs-package.sh` | Builds the `extera-pysdk-stubs` package with type stubs            |

Until version 1.4.6.5 the SDK lived in the `exteraSquad/plugins-pysdk` repository, which keeps its
earlier history (this tree continues from commit `0b5d8d0`). The plugin analyzer and the
documentation site for plugin authors are still developed there.

The SDK is part of exteraGram and is covered by the same license: GPL v3 or later with the
[additional terms](../ADDITIONAL_TERMS.md).

## Build

Every `full` variant has a `buildPythonSdk<Variant>` task that runs `tools/build_sdk.py` and adds
its output to the assets of that variant:

```
plugins_pysdk/v.txt            SDK version
plugins_pysdk/sdk-<abi>.zip    one archive per ABI of the variant
```

An archive holds the modules compiled to sourceless `.pyc` files (`-OO`: no docstrings, no asserts)
and a `v.txt` with `<version>|<1 for beta, 0 for stable>`. On first start the app unpacks the
archive into its files directory and puts that directory on `sys.path`. The archives are the same
for every ABI; they are named per ABI because the app and its update channel address them that way.

Bytecode is tied to the minor version of the interpreter that produced it, so the build has to run
on the Python version the app embeds, currently 3.11, and stops with an error on any other. This is
the interpreter Chaquopy already needs: `python.build` in `local.properties`, the `BUILD_PYTHON`
environment variable, or `python3.11` (`py -3.11` on Windows) on `PATH`.

To build the archives by hand:

```bash
python3.11 plugins-pysdk/tools/build_sdk.py --out plugins-pysdk/build/ota --channel beta --abi arm64-v8a --abi armeabi-v7a
```

## Version and channel

`src/_sdk_version.py` holds the version twice, as `__version__` and as the `version` tuple; the
build fails when they disagree. Raise it with every change that ships: an SDK installed from an
over-the-air update is replaced by the one in the APK only when the APK carries a newer version (or
the same one, if the update was a beta).

The channel is not stored in the tree. `build_sdk.py --channel` writes `__beta__` into the packaged
`_sdk_version` module and into `v.txt`; the Gradle task passes `beta` for the `beta` flavor and
`stable` for every other one. The `__beta__` literal in the source only applies when the modules are
imported straight from `src/`, as the tests and the stub generator do.

The SDK refuses to start on app builds older than exteraGram 12.5.1 and asks the user to update.

## Changing the engine interface

`src/elyxcore/_plugin_engine.py` implements the Kotlin `PluginsController.PluginsEngine` interface
through Chaquopy `@joverride`, and the compiler sees neither side of that contract. A method added
to `PluginsEngine`, `Plugin` or `PluginInstallParams` has to be added here in the same change,
otherwise ElyxEngine fails at runtime.

## Tests

```bash
python3.11 -m unittest discover -s plugins-pysdk/tests
```

## Over-the-air updates

Besides the copy in the APK, the app installs SDK updates posted to its update channel. They run
with the rights of the app, so an update is installed only when it carries a valid signature. The
signed payload is

```
extera-pysdk|<version>|<abi>|<sha256 of the archive>
```

signed with a P-256 key. The public half is pinned in the app
(`TMessagesProj/src/full/java/com/exteragram/messenger/plugins/utils/SdkSignature.kt`); the private
half never enters this repository.

Build the archives for the channel, then sign each one:

```bash
SDK_SIGNING_KEY_FILE=/path/to/sdk-signing-key.pem \
  bash plugins-pysdk/tools/sign-sdk.sh plugins-pysdk/build/ota/plugins_pysdk/sdk-arm64-v8a.zip 1.4.6.5 arm64-v8a beta
```

`sign-sdk.sh` checks that the version and channel on the command line are the ones the archive was
built with, and writes two files next to it:

- `<archive>.sig`: the base64 signature
- `<archive>.message.txt`: the channel message, ready to be posted with the archive

To limit an update to matching app builds, add an `app_version>=13.2.2` line to the message, or
`app_version_code` with the numeric version code of the build. The operators are `>=`, `<=` and
`==`, written without spaces around them. An update whose message has no `signature = ...` line is
ignored by the app.

Rotating the key means generating a new pair, shipping the new public key in an app release, and
keeping the old key until every user is past that release: clients pin exactly one key.

```bash
openssl ecparam -name prime256v1 -genkey -noout -out sdk-signing-key.pem
openssl ec -in sdk-signing-key.pem -pubout -outform DER | base64   # goes into SdkSignature.kt
```

## Type stubs

Plugin authors get editor completion from the `extera-pysdk-stubs` package on PyPI. To build it
(needs `mypy` and `build`):

```bash
STUBS_PACKAGE_VERSION=1.4.6.5 bash plugins-pysdk/tools/build-stubs-package.sh
```

The package lands in `plugins-pysdk/build/stubs-pypi`, ready for `twine upload`, and the bare `.pyi`
files in `plugins-pysdk/build/stubs-files/pyi-files.zip`. With `SDK_CHANNEL=beta` the version gets a
PEP 440 beta suffix.
