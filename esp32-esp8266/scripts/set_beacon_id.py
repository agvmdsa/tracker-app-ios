#!/usr/bin/env python3
"""Sets the beacon ID of a board over its serial port and checks that it took effect.

    esp32-esp8266/.venv/bin/python esp32-esp8266/scripts/set_beacon_id.py --id 9F3C1A01 [--port /dev/cu.usbserial-0001]

It does by itself what the monitor needed typed by hand: wait for the board's banner, send
`id <ID>`, expect `OK id=<ID>`, send `reboot`, and read the new banner to confirm the board now says
`id=<ID>`. A board that already has that ID is left alone. Without `--port` it uses the only USB
serial device it finds. `make flash ID=...` and `make id ID=...` run this (see esp32-esp8266/Makefile).

Protocol: specs/002-esp32-ble-firmware/contracts/serial-protocol.md. Needs pyserial, which the
PlatformIO environment in esp32-esp8266/.venv already has.
"""
import argparse
import re
import sys
import time

_BANNER = re.compile(
    r"^# fw=(?P<fw>\S+) node=(?P<node>\S+) boot=(?P<boot>\d+) role=(?P<role>\S+) id=(?P<id>\S*) baud=(?P<baud>\d+)"
)
_ID = re.compile(r"[0-9A-Fa-f]{8}")


class BoardError(Exception):
    """The board (or the connection to it) did not do what was needed."""


def pyserial_available():
    try:
        import serial  # noqa: F401
    except ImportError:
        return False
    return True


def normalize_id(text):
    """The ID in uppercase; ValueError unless it is exactly 8 hexadecimal characters."""
    if _ID.fullmatch(text) is None:
        raise ValueError(f"the ID must be exactly 8 hexadecimal characters, not {text!r}")
    return text.upper()


def parse_banner(line):
    """The fields of the board's boot banner, or None when `line` is anything else."""
    match = _BANNER.match(line)
    if match is None:
        return None
    info = match.groupdict()
    info["boot"] = int(info["boot"])
    info["baud"] = int(info["baud"])
    return info


def find_port(devices):
    """The only USB serial device, or a BoardError that says what to do."""
    if not devices:
        raise BoardError(
            "no board found on USB: is it powered on (some boards have a switch), is the cable a data "
            "cable, and is it plugged into the Mac? `make ports` lists what the Mac sees"
        )
    if len(devices) > 1:
        raise BoardError(f"several boards are connected ({', '.join(devices)}): choose one with PORT=<device>")
    return devices[0]


class _Lines:
    """Complete lines from a serial port. A read that times out in the middle of a line keeps the
    start of it, so a banner split across two reads is not lost."""

    def __init__(self, port):
        self.port = port
        self.pending = b""

    def read(self):
        chunk = self.port.readline()
        if not chunk:
            return None
        self.pending += chunk
        if not self.pending.endswith(b"\n"):
            return None
        line, self.pending = self.pending, b""
        return line.decode("utf-8", "replace").strip()


def _wait_for(lines, matcher, timeout, clock):
    """The first non-None `matcher(line)` within `timeout` seconds, else None."""
    started = clock()
    while clock() - started < timeout:
        line = lines.read()
        if line is None:
            continue
        found = matcher(line)
        if found is not None:
            return found
    return None


def _reply_to_id(line):
    return line if line.startswith(("OK id=", "ERR ")) else None


def set_beacon_id(port, beacon_id, clock=time.monotonic, banner_timeout=4.0, reply_timeout=3.0,
                  reboot_timeout=10.0, log=lambda message: None):
    """Gives the board on `port` the ID `beacon_id` and confirms it from the banner after a reboot.

    Returns the banner fields plus `changed` (False when the board already had that ID). Raises
    ValueError for an invalid ID (before touching the port) and BoardError when the board misbehaves.
    """
    target = normalize_id(beacon_id)
    lines = _Lines(port)

    # Opening the port resets most boards, so their banner is the first thing to arrive. If it never
    # does, the board may simply have been running already: the commands below still work.
    log("waiting for the board...")
    banner = _wait_for(lines, parse_banner, banner_timeout, clock)
    if banner is not None and banner["id"] == target:
        return {**banner, "changed": False}

    log(f"sending id {target}")
    port.write(f"id {target}\n".encode())
    reply = _wait_for(lines, _reply_to_id, reply_timeout, clock)
    if reply is None:
        raise BoardError(
            "the board did not answer the id command: check that it is powered on, that the cable carries "
            "data and that this is the right port"
        )
    if reply.startswith("ERR"):
        raise BoardError(f"the board refused the ID: {reply}")
    if not reply.startswith(f"OK id={target}"):
        raise BoardError(f"unexpected reply to the id command: {reply}")

    log("rebooting the board")
    port.write(b"reboot\n")
    confirmed = _wait_for(lines, parse_banner, reboot_timeout, clock)
    if confirmed is None:
        raise BoardError("the board did not come back after the reboot (no banner within "
                         f"{reboot_timeout:g} s): unplug it and plug it in again")
    if confirmed["id"] != target:
        raise BoardError(f"after the reboot the banner shows id={confirmed['id']}, expected {target}")
    return {**confirmed, "changed": True}


def main(argv=None, out=sys.stdout, err=sys.stderr):
    parser = argparse.ArgumentParser(description="Set the beacon ID of an ESP32 over serial.")
    parser.add_argument("--id", required=True, metavar="ID", help="8 hexadecimal characters, e.g. 9F3C1A01")
    parser.add_argument("--port", help="serial device (default: the only USB serial device)")
    parser.add_argument("--baud", type=int, default=115200)
    args = parser.parse_args(argv)

    try:
        target = normalize_id(args.id)
    except ValueError as error:
        print(f"error: {error}", file=err)
        return 2
    if not pyserial_available():
        print("error: pyserial is not installed in this Python; run it with esp32-esp8266/.venv/bin/python "
              "(make id and make flash do)", file=err)
        return 2

    import serial
    from serial.tools import list_ports

    def log(message):
        print(message, file=out, flush=True)

    try:
        device = args.port or find_port([p.device for p in list_ports.comports() if p.vid is not None])
        log(f"using {device}")
        with serial.Serial(device, args.baud, timeout=0.2) as port:
            result = set_beacon_id(port, target, log=log)
    except BoardError as error:
        print(f"error: {error}", file=err)
        return 1
    except serial.SerialException as error:
        print(f"error: could not use the serial port: {error} (is a monitor still open? close it with Ctrl+C)",
              file=err)
        return 1

    verb = "now advertises as" if result["changed"] else "already advertises as"
    print(f"OK: board {result['node']} {verb} {result['id']} (boot {result['boot']})", file=out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
