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

import json
import os
import shutil
import socket
import struct
import tempfile
import time
import unittest

import stubs

stubs.install("_sdk_version", "android_utils", "base_plugin", "log_utils")

import dev_server  # noqa: E402

DevServer = dev_server.DevServer


class FakeSocket:
    def __init__(self, data: bytes | None):
        self.data = data

    def getsockopt(self, level, option, size):
        if self.data is None:
            raise OSError("not supported")
        return self.data


class PeerCheckTest(unittest.TestCase):
    def test_only_adb_and_the_app_are_trusted(self):
        self.assertTrue(DevServer._is_trusted_peer(2000))
        self.assertTrue(DevServer._is_trusted_peer(0))
        self.assertTrue(DevServer._is_trusted_peer(os.getuid()))
        self.assertFalse(DevServer._is_trusted_peer(10234))
        self.assertFalse(DevServer._is_trusted_peer(None))

    def test_peer_uid_reads_peer_credentials(self):
        self.assertEqual(DevServer._peer_uid(FakeSocket(struct.pack("3i", 4321, 2000, 2000))), 2000)

    def test_peer_uid_fails_closed(self):
        self.assertIsNone(DevServer._peer_uid(FakeSocket(None)))

    def test_server_uses_an_abstract_socket_without_tcp(self):
        self.assertEqual(DevServer._address(), "\0exteragram_devserver")
        self.assertFalse(hasattr(DevServer, "DEFAULT_PORT"))


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.mkdtemp(dir="/tmp")
        self.addCleanup(shutil.rmtree, self.directory, True)
        self.path = os.path.join(self.directory, "dev.sock")
        self.peer_uid = None
        self.calls = []
        originals = {
            "_address": DevServer.__dict__["_address"],
            "_peer_uid": DevServer.__dict__["_peer_uid"],
            "_handle_write_plugin": DevServer.__dict__["_handle_write_plugin"],
        }
        DevServer._address = classmethod(lambda cls: self.path)
        DevServer._peer_uid = staticmethod(lambda sock: self.peer_uid)
        DevServer._handle_write_plugin = classmethod(lambda cls, command: self.calls.append(command) or {})

        def restore():
            DevServer.stop_server()
            if DevServer._server_thread is not None:
                DevServer._server_thread.join(2)
            for name, value in originals.items():
                setattr(DevServer, name, value)

        self.addCleanup(restore)
        DevServer.start_server()
        deadline = time.time() + 2
        while not os.path.exists(self.path) and time.time() < deadline:
            time.sleep(0.01)

    def request(self, payload: dict) -> bytes:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(2)
            client.connect(self.path)
            client.sendall(json.dumps(payload).encode())
            try:
                return client.recv(4096)
            except (ConnectionResetError, socket.timeout):
                return b""

    def test_untrusted_peer_is_disconnected_without_running_commands(self):
        self.peer_uid = 10234
        reply = self.request({"@": "write_plugin", "#": 1, "plugin_id": "x", "content": "print(1)"})
        self.assertEqual(reply, b"")
        self.assertEqual(self.calls, [])

    def test_adb_peer_is_served(self):
        self.peer_uid = 2000
        reply = self.request({"@": "ping", "#": 7})
        self.assertEqual(json.loads(reply), {"#": 7, "pong": True})

    def test_unknown_peer_uid_is_rejected(self):
        self.peer_uid = None
        self.assertEqual(self.request({"@": "ping", "#": 1}), b"")


if __name__ == "__main__":
    unittest.main()
