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

import ast
import os
import tempfile
import textwrap
import unittest
from unittest import mock

import stubs

stubs.install()

from extera_utils import metadata_parser  # noqa: E402


class MetadataParserTest(unittest.TestCase):
    def parse(self, source: str) -> dict:
        handle, path = tempfile.mkstemp(suffix=".plugin")
        self.addCleanup(os.remove, path)
        with os.fdopen(handle, "w", encoding="utf-8") as output:
            output.write(source)
        return metadata_parser.get_metadata(path)

    def test_reads_top_level_metadata(self):
        metadata = self.parse(textwrap.dedent('''
            """Docstring"""
            try:
                import missing
            except Exception:
                pass

            __id__ = "probe"
            __name__ = "Probe"; __version__ = "1.0"
            __description__ = (
                "first "
                "second"
            )
            __requirements__ = [
                "requests",  # comment
                "pillow",
            ]
            __min_version__ = "12.0.0"

            def setup():
                __id__ = "inner"

            class Plugin:
                __author__ = "inner"
        '''))
        self.assertEqual(metadata, {
            "id": "probe",
            "name": "Probe",
            "version": "1.0",
            "description": "first second",
            "requirements": "requests,pillow",
            "app_version": ">=12.0.0",
        })

    def test_deeply_nested_code_is_never_parsed(self):
        source = '__id__ = "probe"\nx = ' + "-" * 200000 + "1\n__name__ = \"Probe\"\n"
        parsed = []
        original = ast.parse

        def recording_parse(text, *args, **kwargs):
            parsed.append(len(text))
            return original(text, *args, **kwargs)

        with mock.patch.object(metadata_parser.ast, "parse", recording_parse):
            metadata = self.parse(source)
        self.assertEqual(metadata, {"id": "probe", "name": "Probe"})
        self.assertTrue(parsed)
        self.assertLess(max(parsed), 100)

    def test_deep_metadata_value_is_skipped(self):
        metadata = self.parse('__id__ = "probe"\n__name__ = ' + "-" * 200000 + "1\n")
        self.assertEqual(metadata, {"id": "probe"})

    def test_oversized_file_is_rejected_before_reading(self):
        with mock.patch.object(metadata_parser, "MAX_SOURCE_BYTES", 16):
            with self.assertRaisesRegex(Exception, "too large"):
                self.parse('__id__ = "probe"\n' * 4)

    def test_untokenizable_source_is_rejected(self):
        with self.assertRaisesRegex(Exception, "Failed to parse metadata"):
            self.parse('__id__ = "probe"\nx = """unterminated\n')


if __name__ == "__main__":
    unittest.main()
