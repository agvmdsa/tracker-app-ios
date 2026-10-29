// T011: configuration validation and precedence. Constraints (data-model.md), verbatim:
// "windowMs >= 250; advIntervalMs >= 100; scanWindowMs <= scanIntervalMs; nodeId = 6 hex
// characters derived from the board's unique ID unless overridden."
// Precedence (contracts/configuration.md): NVS overrides > build-time defaults.
#include <string.h>
#include <unity.h>

#include "config_validation.h"
#include "device_config.h"

using domain::ConfigError;
using domain::DeviceConfiguration;
using domain::Overrides;
using domain::Role;

void setUp() {}
void tearDown() {}

static DeviceConfiguration validConfig() {
    DeviceConfiguration c{};
    c.role = Role::Beacon;
    strcpy(c.beaconId, "9F3C1A02");
    c.advPowerDbm = 0;
    c.advIntervalMs = 100;
    c.scanIntervalMs = 100;
    c.scanWindowMs = 99;
    c.windowMs = 2000;
    c.serialBaud = 115200;
    c.wifiEnabled = false;
    c.mqttEnabled = false;
    strcpy(c.nodeId, "A1B2C3");
    return c;
}

static void test_valid_configuration_has_no_error() {
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(validConfig()));
}

static void test_window_below_250_is_rejected() {
    DeviceConfiguration c = validConfig();
    c.windowMs = 249;
    TEST_ASSERT_EQUAL(ConfigError::WindowTooShort, domain::validate(c));
    c.windowMs = 250;
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(c));
}

static void test_adv_interval_below_100_is_rejected() {
    DeviceConfiguration c = validConfig();
    c.advIntervalMs = 99;
    TEST_ASSERT_EQUAL(ConfigError::AdvIntervalTooShort, domain::validate(c));
    c.advIntervalMs = 100;
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(c));
}

static void test_scan_window_must_not_exceed_interval() {
    DeviceConfiguration c = validConfig();
    c.scanIntervalMs = 100;
    c.scanWindowMs = 101;
    TEST_ASSERT_EQUAL(ConfigError::ScanWindowExceedsInterval, domain::validate(c));
    c.scanWindowMs = 100;  // equal is allowed
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(c));
}

static void test_node_id_must_be_six_hex_characters() {
    DeviceConfiguration c = validConfig();
    strcpy(c.nodeId, "ABC12");  // 5
    TEST_ASSERT_EQUAL(ConfigError::BadNodeId, domain::validate(c));
    strcpy(c.nodeId, "ABC1234");  // 7
    TEST_ASSERT_EQUAL(ConfigError::BadNodeId, domain::validate(c));
    strcpy(c.nodeId, "ABC12G");  // not hex
    TEST_ASSERT_EQUAL(ConfigError::BadNodeId, domain::validate(c));
    strcpy(c.nodeId, "abc123");  // lowercase hex is fine
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(c));
}

static void test_beacon_role_requires_a_valid_beacon_id() {
    DeviceConfiguration c = validConfig();
    strcpy(c.beaconId, "9F3C1A0");  // 7 characters
    TEST_ASSERT_EQUAL(ConfigError::BadBeaconId, domain::validate(c));
}

static void test_collector_role_does_not_need_a_valid_beacon_id() {
    DeviceConfiguration c = validConfig();
    c.role = Role::Collector;
    strcpy(c.beaconId, "not-an-id");
    TEST_ASSERT_EQUAL(ConfigError::None, domain::validate(c));
}

static void test_no_overrides_keeps_the_defaults() {
    DeviceConfiguration defaults = validConfig();
    Overrides none{};
    DeviceConfiguration resolved = domain::resolveConfig(defaults, none);
    TEST_ASSERT_EQUAL(Role::Beacon, resolved.role);
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", resolved.beaconId);
    TEST_ASSERT_EQUAL_UINT32(2000, resolved.windowMs);
}

static void test_overrides_take_precedence_over_defaults() {
    DeviceConfiguration defaults = validConfig();
    Overrides ov{};
    ov.hasRole = true;
    ov.role = Role::Collector;
    ov.hasBeaconId = true;
    strcpy(ov.beaconId, "AABBCCDD");
    ov.hasWindowMs = true;
    ov.windowMs = 5000;
    ov.hasAdvIntervalMs = true;
    ov.advIntervalMs = 250;
    ov.hasAdvPowerDbm = true;
    ov.advPowerDbm = -6;

    DeviceConfiguration resolved = domain::resolveConfig(defaults, ov);
    TEST_ASSERT_EQUAL(Role::Collector, resolved.role);
    TEST_ASSERT_EQUAL_STRING("AABBCCDD", resolved.beaconId);
    TEST_ASSERT_EQUAL_UINT32(5000, resolved.windowMs);
    TEST_ASSERT_EQUAL_UINT32(250, resolved.advIntervalMs);
    TEST_ASSERT_EQUAL_INT(-6, resolved.advPowerDbm);
    // Untouched fields keep the default.
    TEST_ASSERT_EQUAL_UINT32(115200, resolved.serialBaud);
}

/** Wi-Fi only serves the collector (uploads, MQTT): a beacon never turns it on, so one config.h
 *  with ENABLE_WIFI 1 can be flashed on every board. Wi-Fi on a beacon only shared its radio with
 *  BLE and set off the brownout detector. */
static void test_wifi_is_used_only_by_the_collector() {
    DeviceConfiguration c = validConfig();
    c.wifiEnabled = true;
    c.mqttEnabled = true;

    c.role = Role::Beacon;
    TEST_ASSERT_FALSE(domain::usesWifi(c));
    TEST_ASSERT_FALSE(domain::usesMqtt(c));

    c.role = Role::Collector;
    TEST_ASSERT_TRUE(domain::usesWifi(c));
    TEST_ASSERT_TRUE(domain::usesMqtt(c));
}

static void test_the_collector_uses_wifi_only_when_it_is_enabled() {
    DeviceConfiguration c = validConfig();
    c.role = Role::Collector;
    c.wifiEnabled = false;
    c.mqttEnabled = true;
    TEST_ASSERT_FALSE(domain::usesWifi(c));
    TEST_ASSERT_FALSE(domain::usesMqtt(c));

    c.wifiEnabled = true;
    c.mqttEnabled = false;
    TEST_ASSERT_TRUE(domain::usesWifi(c));
    TEST_ASSERT_FALSE(domain::usesMqtt(c));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_valid_configuration_has_no_error);
    RUN_TEST(test_window_below_250_is_rejected);
    RUN_TEST(test_adv_interval_below_100_is_rejected);
    RUN_TEST(test_scan_window_must_not_exceed_interval);
    RUN_TEST(test_node_id_must_be_six_hex_characters);
    RUN_TEST(test_beacon_role_requires_a_valid_beacon_id);
    RUN_TEST(test_collector_role_does_not_need_a_valid_beacon_id);
    RUN_TEST(test_no_overrides_keeps_the_defaults);
    RUN_TEST(test_overrides_take_precedence_over_defaults);
    RUN_TEST(test_wifi_is_used_only_by_the_collector);
    RUN_TEST(test_the_collector_uses_wifi_only_when_it_is_enabled);
    return UNITY_END();
}
