// T028: beacon role on the host, with a mock radio. Covers US1 acceptance scenarios 1, 3 and 4
// (the radio part) and FR-003, FR-004, FR-018 and FR-019 (specs/002-esp32-ble-firmware/spec.md).
#include <string.h>
#include <unity.h>

#include "../mocks/mocks.h"
#include "app/beacon_role.h"

using domain::DeviceConfiguration;
using domain::Role;

void setUp() {}
void tearDown() {}

static DeviceConfiguration beaconConfig() {
    DeviceConfiguration c{};
    c.role = Role::Beacon;
    strcpy(c.beaconId, "9F3C1A02");
    c.advPowerDbm = 0;
    c.advIntervalMs = 100;
    c.windowMs = 2000;
    strcpy(c.nodeId, "A1B2C3");
    return c;
}

static void test_valid_config_starts_advertising_with_the_id_power_and_interval() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    TEST_ASSERT_TRUE(role.start(beaconConfig()));
    TEST_ASSERT_EQUAL_INT(1, radio.startCalls);
    TEST_ASSERT_TRUE(radio.isAdvertising());
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", radio.last.beaconId);
    TEST_ASSERT_EQUAL_INT(0, radio.last.powerDbm);
    TEST_ASSERT_EQUAL_UINT32(100, radio.last.intervalMs);
}

static void test_a_lowercase_id_is_advertised_in_uppercase() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    DeviceConfiguration c = beaconConfig();
    strcpy(c.beaconId, "9f3c1a02");
    TEST_ASSERT_TRUE(role.start(c));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", radio.last.beaconId);
}

static void test_invalid_id_never_advertises_and_reports_bad_id() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    DeviceConfiguration c = beaconConfig();
    strcpy(c.beaconId, "9F3C1A0");  // 7 characters
    TEST_ASSERT_FALSE(role.start(c));
    TEST_ASSERT_EQUAL_INT(0, radio.startCalls);
    TEST_ASSERT_FALSE(radio.isAdvertising());
    TEST_ASSERT_TRUE(events.hasCode("bad_id"));
    TEST_ASSERT_EQUAL_STRING("error", events.events.back().level.c_str());
}

static void test_interval_below_100_ms_is_refused() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    DeviceConfiguration c = beaconConfig();
    c.advIntervalMs = 99;
    TEST_ASSERT_FALSE(role.start(c));
    TEST_ASSERT_EQUAL_INT(0, radio.startCalls);
    TEST_ASSERT_TRUE(events.hasCode("adv_interval_too_short"));
}

static void test_radio_failure_is_reported() {
    MockBleRadio radio;
    radio.startResult = false;
    CaptureEvents events;
    BeaconRole role(radio, events);
    TEST_ASSERT_FALSE(role.start(beaconConfig()));
    TEST_ASSERT_TRUE(events.hasCode("radio_start_failed"));
}

static void test_startup_reports_id_power_interval_and_packet_size() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    TEST_ASSERT_TRUE(role.start(beaconConfig()));
    TEST_ASSERT_TRUE(events.hasCode("advertising"));
    const std::string& message = events.events.back().message;
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("id=9F3C1A02"));
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("power=0"));
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("interval=100"));
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("packet=31"));
}

static void test_stop_stops_the_radio() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    TEST_ASSERT_TRUE(role.start(beaconConfig()));
    role.stop();
    TEST_ASSERT_EQUAL_INT(1, radio.stopCalls);
    TEST_ASSERT_FALSE(radio.isAdvertising());
}

static void test_role_name_is_beacon() {
    MockBleRadio radio;
    CaptureEvents events;
    BeaconRole role(radio, events);
    TEST_ASSERT_EQUAL_STRING("beacon", role.name());
}

// FR-019: the role is built from the radio and the event sink only. It has no network, queue or
// phone dependency, so a Wi-Fi or MQTT failure cannot reach it by construction.

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_valid_config_starts_advertising_with_the_id_power_and_interval);
    RUN_TEST(test_a_lowercase_id_is_advertised_in_uppercase);
    RUN_TEST(test_invalid_id_never_advertises_and_reports_bad_id);
    RUN_TEST(test_interval_below_100_ms_is_refused);
    RUN_TEST(test_radio_failure_is_reported);
    RUN_TEST(test_startup_reports_id_power_interval_and_packet_size);
    RUN_TEST(test_stop_stops_the_radio);
    RUN_TEST(test_role_name_is_beacon);
    return UNITY_END();
}
