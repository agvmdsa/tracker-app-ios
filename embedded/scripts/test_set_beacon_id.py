"""Tests for set_beacon_id.py. The serial port is a fake that plays the firmware's side of the
protocol, with a fake clock, so nothing waits; one more test talks to a fake board over a real
pseudo-terminal when pyserial is installed (it is in the PlatformIO venv).

Run: python3 -m unittest discover -s embedded/scripts -p "test_set_beacon_id.py"
     embedded/.venv/bin/python -m unittest discover -s embedded/scripts -p "test_set_beacon_id.py"
"""
import collections
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import set_beacon_id as sb  # noqa: E402


def banner(boot, beacon_id, node="BD9E7C", role="beacon"):
    return f"# fw=0.1.0 node={node} boot={boot} role={role} id={beacon_id} baud=115200"


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class FakePort:
    """Plays the board: `initial` is what it printed before we looked, `on_write(command, port)` is
    how it reacts to a command. An empty read is a timeout and moves the clock on."""

    def __init__(self, clock, initial=(), on_write=None):
        self.clock = clock
        self.queue = collections.deque()
        self.writes = []
        self.on_write = on_write or (lambda command, port: None)
        self.say(*initial)

    def say(self, *lines):
        for line in lines:
            self.queue.append(line if isinstance(line, bytes) else (line + "\r\n").encode())

    def readline(self):
        if self.queue:
            return self.queue.popleft()
        self.clock.now += 0.2
        return b""

    def write(self, data):
        self.writes.append(data)
        self.on_write(data.decode().strip(), self)


def well_behaved_board(new_boot=3, node="BD9E7C"):
    """Accepts `id`, and comes back from `reboot` showing the id it was given."""
    state = {}

    def on_write(command, port):
        if command.startswith("id "):
            state["id"] = command.split()[1]
            port.say(f"OK id={state['id']} (applies after reboot)")
        elif command == "reboot":
            port.say("OK rebooting", b"\xff\xfe\x00mode:DIO, clock \xdfj\r\n", "load:0x3fff0030,len:4640",
                     banner(new_boot, state["id"], node))

    return on_write


class NormalizeTest(unittest.TestCase):
    def test_accepts_eight_hex_characters_in_any_case_and_returns_uppercase(self):
        self.assertEqual(sb.normalize_id("9f3c1a01"), "9F3C1A01")
        self.assertEqual(sb.normalize_id("9F3C1A01"), "9F3C1A01")

    def test_rejects_anything_else(self):
        for bad in ["9F3C1A0", "9F3C1A012", "ZZZZZZZZ", "", "9F3C 1A0", "9F3C1A0\n"]:
            with self.assertRaises(ValueError, msg=bad):
                sb.normalize_id(bad)


class ParseBannerTest(unittest.TestCase):
    def test_reads_the_fields_of_the_boot_banner(self):
        info = sb.parse_banner(banner(2, "9F3C1A02"))
        self.assertEqual((info["node"], info["boot"], info["role"], info["id"]), ("BD9E7C", 2, "beacon", "9F3C1A02"))

    def test_anything_else_is_not_a_banner(self):
        for line in ["", "OK rebooting", "E,369,2,info,advertising,id=9F3C1A02 power=0", "mode:DIO, clock �j",
                     "# status state=running_beacon role=beacon id=9F3C1A02"]:
            self.assertIsNone(sb.parse_banner(line), line)


class FindPortTest(unittest.TestCase):
    def test_a_single_board_is_chosen(self):
        self.assertEqual(sb.find_port(["/dev/cu.usbserial-0001"]), "/dev/cu.usbserial-0001")

    def test_no_board_is_an_error_that_says_what_to_check(self):
        with self.assertRaises(sb.BoardError) as caught:
            sb.find_port([])
        self.assertIn("cable", str(caught.exception))

    def test_several_boards_ask_for_the_port(self):
        with self.assertRaises(sb.BoardError) as caught:
            sb.find_port(["/dev/cu.usbserial-0001", "/dev/cu.usbserial-0002"])
        self.assertIn("PORT=", str(caught.exception))


class SetBeaconIdTest(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()

    def run_set(self, port, beacon_id="9F3C1A01"):
        return sb.set_beacon_id(port, beacon_id, clock=self.clock)

    def test_sets_the_id_reboots_and_confirms_it_from_the_banner(self):
        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")], on_write=well_behaved_board())

        result = self.run_set(port)

        self.assertEqual(port.writes, [b"id 9F3C1A01\n", b"reboot\n"])
        self.assertTrue(result["changed"])
        self.assertEqual((result["id"], result["node"], result["boot"]), ("9F3C1A01", "BD9E7C", 3))

    def test_a_lowercase_id_is_sent_in_uppercase(self):
        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")], on_write=well_behaved_board())
        self.run_set(port, "9f3c1a01")
        self.assertEqual(port.writes[0], b"id 9F3C1A01\n")

    def test_a_board_that_already_has_the_id_is_left_alone(self):
        port = FakePort(self.clock, initial=[banner(2, "9F3C1A01")], on_write=well_behaved_board())

        result = self.run_set(port)

        self.assertEqual(port.writes, [], "no command, no reboot")
        self.assertFalse(result["changed"])
        self.assertEqual(result["id"], "9F3C1A01")

    def test_works_when_the_banner_was_missed_because_the_board_was_already_running(self):
        port = FakePort(self.clock, initial=[], on_write=well_behaved_board())

        result = self.run_set(port)

        self.assertEqual(port.writes, [b"id 9F3C1A01\n", b"reboot\n"])
        self.assertEqual(result["id"], "9F3C1A01")

    def test_boot_noise_before_the_banner_is_ignored(self):
        port = FakePort(
            self.clock,
            initial=[b"\xff\xfe\x00 garbage \x80\r\n", "load:0x40078000,len:15632", "E,10,2,info,scanning,x",
                     banner(2, "9F3C1A02")],
            on_write=well_behaved_board(),
        )
        self.assertTrue(self.run_set(port)["changed"])

    def test_a_silent_board_is_an_error_that_says_what_to_check(self):
        port = FakePort(self.clock)

        with self.assertRaises(sb.BoardError) as caught:
            self.run_set(port)

        message = str(caught.exception)
        self.assertIn("did not answer", message)
        self.assertIn("cable", message)

    def test_a_refused_id_is_reported_with_the_boards_reply(self):
        def refuse(command, port):
            if command.startswith("id "):
                port.say("ERR bad_id beacon id must be 8 hexadecimal characters")

        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")], on_write=refuse)

        with self.assertRaises(sb.BoardError) as caught:
            self.run_set(port)

        self.assertIn("ERR bad_id", str(caught.exception))
        self.assertNotIn(b"reboot\n", port.writes)

    def test_a_board_that_does_not_come_back_after_the_reboot_is_an_error(self):
        def dies_on_reboot(command, port):
            if command.startswith("id "):
                port.say("OK id=9F3C1A01 (applies after reboot)")
            elif command == "reboot":
                port.say("OK rebooting")  # and then nothing

        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")], on_write=dies_on_reboot)

        with self.assertRaises(sb.BoardError) as caught:
            self.run_set(port)

        self.assertIn("did not come back", str(caught.exception))

    def test_a_banner_with_another_id_after_the_reboot_is_an_error(self):
        def forgets(command, port):
            if command.startswith("id "):
                port.say("OK id=9F3C1A01 (applies after reboot)")
            elif command == "reboot":
                port.say("OK rebooting", banner(3, "9F3C1A02"))  # the setting did not stick

        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")], on_write=forgets)

        with self.assertRaises(sb.BoardError) as caught:
            self.run_set(port)

        self.assertIn("9F3C1A02", str(caught.exception))
        self.assertIn("9F3C1A01", str(caught.exception))

    def test_an_invalid_id_never_touches_the_port(self):
        port = FakePort(self.clock, initial=[banner(2, "9F3C1A02")])
        with self.assertRaises(ValueError):
            self.run_set(port, "9F3C1A0")
        self.assertEqual(port.writes, [])


@unittest.skipUnless(sb.pyserial_available(), "pyserial is not installed in this Python")
class PseudoTerminalTest(unittest.TestCase):
    """The real pyserial calls (open, readline with a timeout, write) against a fake board."""

    def test_sets_the_id_of_a_fake_board_over_a_pseudo_terminal(self):
        import pty
        import select
        import threading
        import time

        import serial

        master, slave = pty.openpty()
        slave_path = os.ttyname(slave)
        stop = threading.Event()
        beacon_id = {"value": "9F3C1A02"}

        def say(text):
            os.write(master, text.encode() + b"\r\n")

        def board():
            say("mode:DIO, clock garbage")
            say(banner(1, beacon_id["value"]))
            pending = b""
            while not stop.is_set():
                ready, _, _ = select.select([master], [], [], 0.05)
                if not ready:
                    continue
                pending += os.read(master, 256)
                while b"\n" in pending:
                    line, pending = pending.split(b"\n", 1)
                    command = line.decode().strip()
                    if command.startswith("id "):
                        beacon_id["value"] = command.split()[1]
                        say(f"OK id={beacon_id['value']} (applies after reboot)")
                    elif command == "reboot":
                        say("OK rebooting")
                        time.sleep(0.2)
                        say(banner(2, beacon_id["value"]))

        thread = threading.Thread(target=board, daemon=True)
        thread.start()
        try:
            with serial.Serial(slave_path, 115200, timeout=0.2) as port:
                result = sb.set_beacon_id(port, "9F3C1A01", banner_timeout=3, reply_timeout=3, reboot_timeout=5)
        finally:
            stop.set()
            thread.join(timeout=2)
            os.close(master)
            os.close(slave)

        self.assertTrue(result["changed"])
        self.assertEqual(result["id"], "9F3C1A01")
        self.assertEqual(result["boot"], 2)


if __name__ == "__main__":
    unittest.main()
