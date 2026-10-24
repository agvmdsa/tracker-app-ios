// Supervisor behaviour on the host: the device state machine (data-model.md), the boot banner and
// the generic serial commands (contracts/serial-protocol.md).
#include <string.h>
#include <unity.h>

#include "../mocks/mocks.h"
#include "app/role_factory.h"
#include "app/supervisor.h"

using domain::DeviceConfiguration;
using domain::Role;

void setUp() {}
void tearDown() {}

namespace {

DeviceConfiguration defaultsConfig() {
    DeviceConfiguration c{};
    c.role = Role::Beacon;
    strcpy(c.beaconId, "9F3C1A02");
    c.advIntervalMs = 100;
    c.scanIntervalMs = 100;
    c.scanWindowMs = 99;
    c.windowMs = 2000;
    c.serialBaud = 115200;
    return c;  // nodeId left empty on purpose: the supervisor derives it
}

struct Rig {
    FakeClock clock;
    FakeStore store;
    FakeSystem system;
    CapturePublisher out;
    ScriptedCommands commands;
    FakeRole beacon;
    FakeRole collector;
    FakeRecording recording;
    RoleFactory factory;
    Supervisor supervisor;

    explicit Rig(DeviceConfiguration defaults = defaultsConfig(), bool withCollector = true)
        : factory(&beacon, withCollector ? &collector : nullptr),
          supervisor(defaults, store, clock, system, out, commands, factory, "0.1.0") {
        beacon.label = "beacon";
        collector.label = "collector";
        collector.control = &recording;
    }

    void runAsCollector() {
        store.overrides.hasRole = true;
        store.overrides.role = Role::Collector;
        supervisor.begin();
    }
};

}  // namespace

static void test_valid_config_starts_the_beacon_role_and_prints_the_banner() {
    Rig rig;
    rig.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::RunningBeacon, rig.supervisor.state());
    TEST_ASSERT_EQUAL_INT(1, rig.beacon.starts);
    TEST_ASSERT_EQUAL_INT(0, rig.collector.starts);
    TEST_ASSERT_EQUAL_UINT32(1, rig.store.boots);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("# fw=0.1.0 node=445566 boot=1 role=beacon id=9F3C1A02 baud=115200"));
}

static void test_override_selects_the_collector_role() {
    Rig rig;
    rig.store.overrides.hasRole = true;
    rig.store.overrides.role = Role::Collector;
    rig.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::RunningCollector, rig.supervisor.state());
    TEST_ASSERT_EQUAL_INT(1, rig.collector.starts);
    TEST_ASSERT_EQUAL_INT(0, rig.beacon.starts);
}

static void test_invalid_beacon_id_enters_config_error_and_starts_no_role() {
    Rig rig;
    rig.store.overrides.hasBeaconId = true;
    strcpy(rig.store.overrides.beaconId, "9F3C1A0");  // 7 characters
    rig.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::ConfigError, rig.supervisor.state());
    TEST_ASSERT_EQUAL_INT(0, rig.beacon.starts);
    TEST_ASSERT_TRUE(rig.out.hasLineContaining(",error,bad_id,"));
}

static void test_commands_still_work_in_config_error() {
    Rig rig;
    rig.store.overrides.hasBeaconId = true;
    strcpy(rig.store.overrides.beaconId, "ZZZZZZZZ");
    rig.supervisor.begin();
    rig.commands.push("status");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("# status state=config_error"));
}

static void test_role_unavailable_is_reported() {
    Rig rig(defaultsConfig(), /*withCollector=*/false);
    rig.store.overrides.hasRole = true;
    rig.store.overrides.role = Role::Collector;
    rig.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::ConfigError, rig.supervisor.state());
    TEST_ASSERT_TRUE(rig.out.hasLineContaining(",error,role_unavailable,"));
}

static void test_help_lists_the_commands() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("help");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("# help"));
    TEST_ASSERT_TRUE(rig.out.hasLineContaining("reboot"));
}

static void test_status_shows_configuration_without_secrets() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("status");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("# status state=running_beacon role=beacon id=9F3C1A02"));
    TEST_ASSERT_TRUE(rig.out.hasLineContaining("heap_free=150000"));
    TEST_ASSERT_FALSE(rig.out.hasLineContaining("password"));
}

static void test_role_command_persists_the_override() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("role collector");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.store.overrides.hasRole);
    TEST_ASSERT_EQUAL(Role::Collector, rig.store.overrides.role);
    TEST_ASSERT_GREATER_THAN_INT(0, rig.store.saves);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK role=collector"));
}

static void test_config_reset_clears_the_overrides() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("config reset");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(1, rig.store.clears);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK config reset"));
}

static void test_config_save_confirms() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("config save");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK config saved"));
}

static void test_reboot_asks_the_system_to_restart() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("reboot");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.system.rebooted);
}

static void test_parse_errors_are_answered_with_err() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("frobnicate");
    rig.commands.push("role banana");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR unknown_command"));
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR bad_value"));
}

static void test_empty_lines_are_ignored() {
    Rig rig;
    rig.supervisor.begin();
    const size_t before = rig.out.lines.size();
    rig.commands.push("   ");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_UINT32(before, rig.out.lines.size());
}

// --- T031: beacon settings commands (id, power, interval) ---

static void test_id_command_persists_the_uppercase_override() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("id 9f3c1a03");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.store.overrides.hasBeaconId);
    TEST_ASSERT_EQUAL_STRING("9F3C1A03", rig.store.overrides.beaconId);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK id=9F3C1A03"));
}

static void test_invalid_id_command_changes_nothing() {
    Rig rig;
    rig.supervisor.begin();
    const int savesBefore = rig.store.saves;
    rig.commands.push("id 9F3C1A0");
    rig.supervisor.tick();
    TEST_ASSERT_FALSE(rig.store.overrides.hasBeaconId);
    TEST_ASSERT_EQUAL_INT(savesBefore, rig.store.saves);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR bad_id"));
}

static void test_power_command_persists_the_override() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("power -6");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.store.overrides.hasAdvPowerDbm);
    TEST_ASSERT_EQUAL_INT(-6, rig.store.overrides.advPowerDbm);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK power=-6"));
}

static void test_interval_command_persists_the_override() {
    Rig rig;
    rig.supervisor.begin();
    rig.commands.push("interval 250");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.store.overrides.hasAdvIntervalMs);
    TEST_ASSERT_EQUAL_UINT32(250, rig.store.overrides.advIntervalMs);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK interval=250"));
}

static void test_a_fixed_id_override_recovers_from_config_error_after_reboot() {
    Rig bad;
    bad.store.overrides.hasBeaconId = true;
    strcpy(bad.store.overrides.beaconId, "9F3C1A0");
    bad.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::ConfigError, bad.supervisor.state());
    bad.commands.push("id 9F3C1A04");
    bad.supervisor.tick();  // the command still works in ConfigError and persists the fix

    // Next boot reads the repaired override from the same store.
    Rig next;
    next.store.overrides = bad.store.overrides;
    next.supervisor.begin();
    TEST_ASSERT_EQUAL(Supervisor::State::RunningBeacon, next.supervisor.state());
    TEST_ASSERT_EQUAL_STRING("9F3C1A04", next.beacon.lastConfig.beaconId);
}

// US2: the collector's commands and the role's periodic work.

static void test_tick_polls_the_running_role() {
    Rig rig;
    rig.supervisor.begin();
    rig.supervisor.tick();
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(2, rig.beacon.polls);
    TEST_ASSERT_EQUAL_INT(0, rig.collector.polls);
}

static void test_tick_polls_nothing_when_no_role_is_running() {
    Rig rig;
    rig.store.overrides.hasBeaconId = true;
    strcpy(rig.store.overrides.beaconId, "ZZZZZZZZ");
    rig.supervisor.begin();
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(0, rig.beacon.polls);
}

static void test_label_command_sets_the_label_on_the_collector() {
    Rig rig;
    rig.runAsCollector();
    rig.commands.push("label S1 P3 2.0 4.5");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(1, rig.recording.labels);
    TEST_ASSERT_EQUAL_STRING("S1", rig.recording.lastLabel.session);
    TEST_ASSERT_EQUAL_STRING("P3", rig.recording.lastLabel.point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, rig.recording.lastLabel.x);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 4.5f, rig.recording.lastLabel.y);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK label=S1,P3,2.00,4.50"));
}

static void test_label_and_rec_need_the_collector_role() {
    Rig rig;
    rig.supervisor.begin();  // beacon role: no recording half
    rig.commands.push("label S1 P3 2.0 4.5");
    rig.commands.push("rec start");
    rig.commands.push("rec stop");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(0, rig.recording.labels);
    TEST_ASSERT_EQUAL_INT(0, rig.recording.starts);
    int refusals = 0;
    for (const auto& l : rig.out.lines) {
        if (l.rfind("ERR not_collector", 0) == 0) ++refusals;
    }
    TEST_ASSERT_EQUAL_INT(3, refusals);
}

static void test_label_with_invalid_characters_never_reaches_the_collector() {
    Rig rig;
    rig.runAsCollector();
    rig.commands.push("label S,1 P3 2.0 4.5");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(0, rig.recording.labels);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR bad_label"));
}

static void test_rec_start_confirms_with_the_active_label() {
    Rig rig;
    rig.runAsCollector();
    rig.commands.push("label S1 P3 2.0 4.5");
    rig.commands.push("rec start");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(1, rig.recording.starts);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK recording started"));
}

static void test_rec_start_without_a_label_answers_no_label() {
    Rig rig;
    rig.runAsCollector();
    rig.recording.startResult = domain::RecordingState::StartResult::NoLabel;
    rig.commands.push("rec start");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR no_label"));
}

static void test_rec_start_twice_answers_already_recording() {
    Rig rig;
    rig.runAsCollector();
    rig.recording.startResult = domain::RecordingState::StartResult::AlreadyRecording;
    rig.commands.push("rec start");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR already_recording"));
}

static void test_rec_stop_reports_the_totals() {
    Rig rig;
    rig.runAsCollector();
    rig.recording.summary.records = 4;
    rig.recording.summary.beacons = 2;
    rig.commands.push("rec stop");
    rig.supervisor.tick();
    TEST_ASSERT_EQUAL_INT(1, rig.recording.stops);
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("OK recording stopped records=4 beacons=2"));
}

static void test_rec_stop_without_a_recording_answers_not_recording() {
    Rig rig;
    rig.runAsCollector();
    rig.recording.stopResult = false;
    rig.commands.push("rec stop");
    rig.supervisor.tick();
    TEST_ASSERT_TRUE(rig.out.hasLineStartingWith("ERR not_recording"));
}

static void test_the_boot_number_is_given_to_the_role() {
    Rig rig;
    rig.runAsCollector();
    TEST_ASSERT_EQUAL_UINT32(1, rig.collector.lastConfig.bootId);
    TEST_ASSERT_EQUAL_STRING("445566", rig.collector.lastConfig.nodeId);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_valid_config_starts_the_beacon_role_and_prints_the_banner);
    RUN_TEST(test_override_selects_the_collector_role);
    RUN_TEST(test_invalid_beacon_id_enters_config_error_and_starts_no_role);
    RUN_TEST(test_commands_still_work_in_config_error);
    RUN_TEST(test_role_unavailable_is_reported);
    RUN_TEST(test_help_lists_the_commands);
    RUN_TEST(test_status_shows_configuration_without_secrets);
    RUN_TEST(test_role_command_persists_the_override);
    RUN_TEST(test_config_reset_clears_the_overrides);
    RUN_TEST(test_config_save_confirms);
    RUN_TEST(test_reboot_asks_the_system_to_restart);
    RUN_TEST(test_parse_errors_are_answered_with_err);
    RUN_TEST(test_empty_lines_are_ignored);
    RUN_TEST(test_id_command_persists_the_uppercase_override);
    RUN_TEST(test_invalid_id_command_changes_nothing);
    RUN_TEST(test_power_command_persists_the_override);
    RUN_TEST(test_interval_command_persists_the_override);
    RUN_TEST(test_a_fixed_id_override_recovers_from_config_error_after_reboot);
    RUN_TEST(test_tick_polls_the_running_role);
    RUN_TEST(test_tick_polls_nothing_when_no_role_is_running);
    RUN_TEST(test_label_command_sets_the_label_on_the_collector);
    RUN_TEST(test_label_and_rec_need_the_collector_role);
    RUN_TEST(test_label_with_invalid_characters_never_reaches_the_collector);
    RUN_TEST(test_rec_start_confirms_with_the_active_label);
    RUN_TEST(test_rec_start_without_a_label_answers_no_label);
    RUN_TEST(test_rec_start_twice_answers_already_recording);
    RUN_TEST(test_rec_stop_reports_the_totals);
    RUN_TEST(test_rec_stop_without_a_recording_answers_not_recording);
    RUN_TEST(test_the_boot_number_is_given_to_the_role);
    return UNITY_END();
}
