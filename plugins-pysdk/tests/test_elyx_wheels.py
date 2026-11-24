# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

from __future__ import annotations

import io
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace

import stubs

stubs.install("_sdk_version", "android_utils", "base_plugin", "log_utils")

import elyxcore._importer as importer_module  # noqa: E402
import elyxcore._plugin as plugin_module  # noqa: E402


def wheel(value: int) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("probe_dep.py", f"VALUE = {value}\n")
    return buffer.getvalue()


class ElyxWheelsTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, True)
        self.plugins_dir = self.root / "plugins"
        self.plugin_dir = self.root / "elyx" / "probe"
        (self.plugin_dir / "wheels").mkdir(parents=True)
        controller = SimpleNamespace(pluginsDir=str(self.plugins_dir))
        original = plugin_module.PluginsController
        plugin_module.PluginsController = SimpleNamespace(getInstance=lambda: controller)
        self.addCleanup(setattr, plugin_module, "PluginsController", original)
        saved_path = list(sys.path)
        self.addCleanup(sys.path.__setitem__, slice(None), saved_path)
        self.plugin = SimpleNamespace(dir_path=str(self.plugin_dir), structure=SimpleNamespace(wheels_path="wheels"))
        self.lib_dir = self.plugins_dir / "elyx_local_libs" / "probe"

    def ship(self, value: int | None) -> None:
        for old in (self.plugin_dir / "wheels").glob("*.whl"):
            old.unlink()
        if value is not None:
            (self.plugin_dir / "wheels" / "probe_dep-1.0-py3-none-any.whl").write_bytes(wheel(value))

    def imported_value(self) -> str:
        roots = plugin_module._read_wheel_roots("probe")
        self.assertEqual(len(roots), 1)
        return (roots[0] / "probe_dep.py").read_text()

    def test_update_with_same_wheel_name_replaces_the_code(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.assertEqual(self.imported_value(), "VALUE = 1\n")
        old_root = plugin_module._read_wheel_roots("probe")[0]

        self.ship(2)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.assertEqual(self.imported_value(), "VALUE = 2\n")
        self.assertFalse(old_root.exists())
        self.assertNotIn(str(old_root), sys.path)

    def test_rollback_restores_the_previous_code(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.ship(2)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.assertEqual(self.imported_value(), "VALUE = 1\n")

    def test_version_without_wheels_removes_extracted_code(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        root = plugin_module._read_wheel_roots("probe")[0]
        self.ship(None)
        plugin_module._install_local_wheels(self.plugin, "probe")
        self.assertEqual(plugin_module._read_wheel_roots("probe"), ())
        self.assertFalse(self.lib_dir.exists())
        self.assertNotIn(str(root), sys.path)

    def test_clear_removes_wheels_left_by_a_deleted_plugin(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        plugin_module._clear_local_wheels("probe")
        self.assertFalse(self.lib_dir.exists())

    def test_directories_outside_the_manifest_are_not_import_roots(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        planted = self.lib_dir / "probe_dep-1.0-py3-none-any"
        planted.mkdir()
        (planted / "probe_dep.py").write_text("VALUE = 666\n")
        self.assertEqual(self.imported_value(), "VALUE = 1\n")

        importer = importer_module.importer
        importer.invalidate_caches()
        roots = importer._get_wheel_roots("probe")
        self.assertNotIn(planted, roots)
        self.assertEqual(len(roots), 1)

    def test_missing_or_malformed_manifest_gives_no_roots(self):
        self.ship(1)
        plugin_module._install_local_wheels(self.plugin, "probe")
        manifest = self.lib_dir / plugin_module.WHEEL_ROOTS_FILE
        manifest.write_text('["../../elyx/probe", "."]')
        self.assertEqual(plugin_module._read_wheel_roots("probe"), ())
        manifest.unlink()
        self.assertEqual(plugin_module._read_wheel_roots("probe"), ())


if __name__ == "__main__":
    unittest.main()
