import unittest

from pick_device import PickError, pick


def device(name, identifier, transport=None, platform="iOS", paired=True, udid=None):
    return {
        "identifier": identifier,
        "deviceProperties": {"name": name},
        "hardwareProperties": {"platform": platform, "udid": udid or identifier + "-udid", "marketingName": "iPhone"},
        "connectionProperties": {"pairingState": "paired" if paired else "unpaired", "transportType": transport},
    }


class PickTests(unittest.TestCase):
    def test_the_only_reachable_iphone_is_picked(self):
        devices = [device("Old", "A"), device("Mine", "B", transport="wired"), device("Far", "C")]
        self.assertEqual(pick(devices), "B")

    def test_one_on_the_local_network_counts_as_reachable(self):
        self.assertEqual(pick([device("Mine", "B", transport="localNetwork")]), "B")

    def test_none_reachable_says_how_to_connect(self):
        with self.assertRaisesRegex(PickError, "Ligue o cabo"):
            pick([device("Old", "A")])

    def test_several_reachable_asks_to_pick_one_and_lists_them(self):
        devices = [device("One", "A", transport="wired"), device("Two", "B", transport="wired")]
        with self.assertRaisesRegex(PickError, "Ha 2 iPhones") as raised:
            pick(devices)
        self.assertIn("One", str(raised.exception))
        self.assertIn("Two", str(raised.exception))

    def test_a_device_asked_for_by_name_identifier_or_udid(self):
        devices = [device("One", "A", transport="wired", udid="U1"), device("Two", "B", transport="wired")]
        self.assertEqual(pick(devices, "Two"), "B")
        self.assertEqual(pick(devices, "A"), "A")
        self.assertEqual(pick(devices, "U1"), "A")

    def test_a_device_asked_for_but_not_connected(self):
        with self.assertRaisesRegex(PickError, "nao esta acessivel"):
            pick([device("Two", "B")], "Two")

    def test_an_unknown_device(self):
        with self.assertRaisesRegex(PickError, "Nenhum iPhone chamado"):
            pick([device("Two", "B", transport="wired")], "Three")

    def test_macs_and_unpaired_devices_are_left_out(self):
        devices = [
            device("Mac", "M", transport="wired", platform="macOS"),
            device("Stranger", "S", transport="wired", paired=False),
            device("Mine", "B", transport="wired"),
        ]
        self.assertEqual(pick(devices), "B")


if __name__ == "__main__":
    unittest.main()
