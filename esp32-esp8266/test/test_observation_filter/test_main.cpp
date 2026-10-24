// T038: observation filter on the host. Constraint (contracts/ble-advertising.md), verbatim: "An
// advertisement becomes an Observation only if both hold: 1. the service UUID is present, and 2. the
// local name is exactly 8 hexadecimal characters." Identity is the ID, never the sender's address,
// and the RSSI is never altered (spec edge case).
#include <string.h>
#include <unity.h>

#include "../mocks/adv_payload.h"
#include "observation_filter.h"

using domain::Observation;
using domain::observeAdvertisement;

void setUp() {}
void tearDown() {}

static bool observe(const AdPayload& payload, int rssi, uint32_t tMs, Observation& out) {
    return observeAdvertisement(payload.data(), payload.size(), rssi, tMs, out);
}

static void test_service_uuid_constant_is_the_contracts_uuid_in_advertisement_byte_order() {
    // fc979812-36dd-4722-9316-d2a7908888dd, least significant byte first.
    const uint8_t expected[16] = {0xdd, 0x88, 0x88, 0x90, 0xa7, 0xd2, 0x16, 0x93,
                                  0x22, 0x47, 0xdd, 0x36, 0x12, 0x98, 0x97, 0xfc};
    TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, domain::kProjectServiceUuid, 16);
}

static void test_accepts_the_beacons_own_31_byte_packet() {
    const AdPayload packet = AdPayload::beaconPacket("9F3C1A02");
    TEST_ASSERT_EQUAL_UINT(31, packet.size());
    Observation o;
    TEST_ASSERT_TRUE(observe(packet, -63, 4200, o));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", o.beacon.value);
    TEST_ASSERT_EQUAL_INT(-63, o.rssi);
    TEST_ASSERT_EQUAL_UINT32(4200, o.tMs);
}

static void test_a_lowercase_name_gives_the_same_uppercase_identity() {
    Observation lower, upper;
    TEST_ASSERT_TRUE(observe(AdPayload::beaconPacket("9f3c1a02"), -60, 1, lower));
    TEST_ASSERT_TRUE(observe(AdPayload::beaconPacket("9F3C1A02"), -60, 2, upper));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", lower.beacon.value);
    TEST_ASSERT_EQUAL_STRING(upper.beacon.value, lower.beacon.value);
}

static void test_identity_does_not_depend_on_field_order_or_unrelated_fields() {
    // An iOS anchor and an ESP32 beacon lay the same ID out differently; both are one identity.
    const uint8_t manufacturer[3] = {0x4c, 0x00, 0x01};
    AdPayload other;
    other.name("9F3C1A02").field(0xff, manufacturer, sizeof(manufacturer)).projectServiceUuid();
    Observation a, b;
    TEST_ASSERT_TRUE(observe(AdPayload::beaconPacket("9F3C1A02"), -60, 1, a));
    TEST_ASSERT_TRUE(observe(other, -71, 2, b));
    TEST_ASSERT_EQUAL_STRING(a.beacon.value, b.beacon.value);
}

static void test_a_name_carried_in_the_scan_response_is_read() {
    // An iOS anchor may put the UUID in the advertisement and the name in the scan response; the
    // scanner hands over both concatenated.
    AdPayload merged = AdPayload().flags().projectServiceUuid();
    merged.append(AdPayload().name("0A1B2C3D"));
    Observation o;
    TEST_ASSERT_TRUE(observe(merged, -70, 10, o));
    TEST_ASSERT_EQUAL_STRING("0A1B2C3D", o.beacon.value);
}

static void test_a_shortened_name_of_eight_hex_characters_counts_as_the_local_name() {
    // The iOS scanner reads the local name whatever its form; the service UUID guards the match.
    Observation o;
    TEST_ASSERT_TRUE(observe(AdPayload().flags().projectServiceUuid().name("9F3C1A02", /*complete=*/false), -60, 1, o));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", o.beacon.value);
}

static void test_the_uuid_in_an_incomplete_list_or_among_several_uuids_counts() {
    const uint8_t other[16] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    uint8_t two[32];
    memcpy(two, other, 16);
    memcpy(two + 16, domain::kProjectServiceUuid, 16);

    Observation o;
    TEST_ASSERT_TRUE(observe(AdPayload().serviceUuid(domain::kProjectServiceUuid, /*complete=*/false).name("9F3C1A02"), -60, 1, o));
    TEST_ASSERT_TRUE(observe(AdPayload().field(0x07, two, sizeof(two)).name("9F3C1A02"), -60, 2, o));
}

static void test_rejects_an_advertisement_without_the_service_uuid() {
    Observation o;
    TEST_ASSERT_FALSE(observe(AdPayload().flags().name("9F3C1A02"), -60, 1, o));
}

static void test_rejects_another_services_uuid() {
    // The iOS UWB handshake uses a different service UUID on purpose.
    const uint8_t handshake[16] = {0x10, 0x9a, 0x0e, 0x5f, 0x7a, 0x2b, 0x1d, 0x9c,
                                   0x8e, 0x4b, 0x4b, 0x7c, 0x52, 0x3a, 0x1f, 0x6e};
    Observation o;
    TEST_ASSERT_FALSE(observe(AdPayload().flags().serviceUuid(handshake).name("9F3C1A02"), -60, 1, o));
}

static void test_rejects_names_that_are_not_exactly_eight_hex_characters() {
    Observation o;
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("9F3C1A0"), -60, 1, o));    // 7
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("9F3C1A023"), -60, 1, o));  // 9
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("ZZZZZZZZ"), -60, 1, o));   // not hex
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("9F3C 1A0"), -60, 1, o));   // space
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("iPhone"), -60, 1, o));
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket(""), -60, 1, o));           // empty name
}

static void test_rejects_an_advertisement_without_a_name() {
    Observation o;
    TEST_ASSERT_FALSE(observe(AdPayload().flags().projectServiceUuid(), -60, 1, o));
}

static void test_a_rejected_advertisement_leaves_the_output_unchanged() {
    Observation o;
    TEST_ASSERT_TRUE(observe(AdPayload::beaconPacket("9F3C1A02"), -50, 99, o));
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("ZZZZZZZZ"), -80, 100, o));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", o.beacon.value);
    TEST_ASSERT_EQUAL_INT(-50, o.rssi);
    TEST_ASSERT_EQUAL_UINT32(99, o.tMs);
}

static void test_rssi_is_never_altered() {
    const int values[] = {-127, -100, -90, -59, -1, 0, 20, 127};
    for (int rssi : values) {
        Observation o;
        TEST_ASSERT_TRUE(observe(AdPayload::beaconPacket("9F3C1A02"), rssi, 1, o));
        TEST_ASSERT_EQUAL_INT(rssi, o.rssi);
    }
}

static void test_a_value_that_does_not_fit_an_rssi_is_refused_not_wrapped() {
    Observation o;
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("9F3C1A02"), 200, 1, o));   // would wrap to -56
    TEST_ASSERT_FALSE(observe(AdPayload::beaconPacket("9F3C1A02"), -200, 1, o));  // would wrap to 56
}

static void test_malformed_payloads_are_ignored_without_reading_out_of_bounds() {
    Observation o;
    // A length byte that runs past the end of the payload.
    TEST_ASSERT_FALSE(observe(AdPayload().raw({0x1f, 0x09, '9', 'F', '3'}), -60, 1, o));
    // A UUID list shorter than one 128-bit UUID.
    TEST_ASSERT_FALSE(observe(AdPayload().raw({0x08, 0x07, 0xdd, 0x88, 0x88, 0x90, 0xa7, 0xd2, 0x16}), -60, 1, o));
    // A zero length ends the payload (padding); nothing after it counts.
    AdPayload padded;
    padded.flags().raw({0x00}).projectServiceUuid().name("9F3C1A02");
    TEST_ASSERT_FALSE(observe(padded, -60, 1, o));
    // Empty payload and a null pointer.
    TEST_ASSERT_FALSE(observeAdvertisement(nullptr, 0, -60, 1, o));
    TEST_ASSERT_FALSE(observeAdvertisement(reinterpret_cast<const uint8_t*>(""), 0, -60, 1, o));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_service_uuid_constant_is_the_contracts_uuid_in_advertisement_byte_order);
    RUN_TEST(test_accepts_the_beacons_own_31_byte_packet);
    RUN_TEST(test_a_lowercase_name_gives_the_same_uppercase_identity);
    RUN_TEST(test_identity_does_not_depend_on_field_order_or_unrelated_fields);
    RUN_TEST(test_a_name_carried_in_the_scan_response_is_read);
    RUN_TEST(test_a_shortened_name_of_eight_hex_characters_counts_as_the_local_name);
    RUN_TEST(test_the_uuid_in_an_incomplete_list_or_among_several_uuids_counts);
    RUN_TEST(test_rejects_an_advertisement_without_the_service_uuid);
    RUN_TEST(test_rejects_another_services_uuid);
    RUN_TEST(test_rejects_names_that_are_not_exactly_eight_hex_characters);
    RUN_TEST(test_rejects_an_advertisement_without_a_name);
    RUN_TEST(test_a_rejected_advertisement_leaves_the_output_unchanged);
    RUN_TEST(test_rssi_is_never_altered);
    RUN_TEST(test_a_value_that_does_not_fit_an_rssi_is_refused_not_wrapped);
    RUN_TEST(test_malformed_payloads_are_ignored_without_reading_out_of_bounds);
    return UNITY_END();
}
