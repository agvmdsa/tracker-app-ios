#!/usr/bin/env python3
"""Picks the iPhone to install on, from `xcrun devicectl list devices`.

Used by ios/Makefile. Prints the CoreDevice identifier of the one device that is reachable now
(over the cable or the local network), or the one asked for with --device (its name, identifier
or UDID). Exits 1 with a plain message when there is none, or more than one and none was asked for.
"""

import argparse
import json
import subprocess
import sys
import tempfile


class PickError(Exception):
    pass


def reachable(device):
    """Paired and connected over a transport (wired or local network) right now."""
    connection = device.get("connectionProperties", {})
    return connection.get("pairingState") == "paired" and connection.get("transportType") not in (None, "")


def describe(device):
    name = device.get("deviceProperties", {}).get("name", "?")
    model = device.get("hardwareProperties", {}).get("marketingName", "")
    transport = device.get("connectionProperties", {}).get("transportType") or "desconectado"
    return f"{name} ({model}, {transport}) {device.get('identifier')}"


def pick(devices, wanted=None):
    """The identifier of the device to use: `wanted` (name, identifier or UDID) if given,
    otherwise the only reachable iPhone or iPad."""
    ios = [d for d in devices if d.get("hardwareProperties", {}).get("platform") == "iOS"]
    if wanted:
        for device in ios:
            keys = {
                device.get("identifier"),
                device.get("hardwareProperties", {}).get("udid"),
                device.get("deviceProperties", {}).get("name"),
            }
            if wanted in keys:
                if not reachable(device):
                    raise PickError(f"{describe(device)} nao esta acessivel: desbloqueie e ligue o cabo.")
                return device["identifier"]
        raise PickError(f"Nenhum iPhone chamado {wanted!r}. Veja make devices.")
    ready = [d for d in ios if reachable(d)]
    if not ready:
        raise PickError(
            "Nenhum iPhone acessivel. Ligue o cabo, desbloqueie, toque em Confiar nele "
            "e confira com make devices."
        )
    if len(ready) > 1:
        listing = "\n".join("  " + describe(d) for d in ready)
        raise PickError(f"Ha {len(ready)} iPhones acessiveis. Escolha um: make run DEVICE=<nome ou identificador>\n{listing}")
    return ready[0]["identifier"]


def list_devices():
    with tempfile.NamedTemporaryFile(suffix=".json") as output:
        subprocess.run(
            ["xcrun", "devicectl", "list", "devices", "--json-output", output.name],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with open(output.name) as file:
            return json.load(file)["result"]["devices"]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", default="", help="name, identifier or UDID; empty picks the only reachable one")
    args = parser.parse_args(argv)
    try:
        print(pick(list_devices(), args.device or None))
    except PickError as error:
        print(error, file=sys.stderr)
        return 1
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("Nao deu para rodar o xcrun devicectl: instale o Xcode 15 ou mais novo e abra-o uma vez.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
