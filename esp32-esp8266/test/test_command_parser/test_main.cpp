// T014: serial command parser. One case per command of contracts/serial-protocol.md.
#include <unity.h>

#include "command_parser.h"

using domain::Command;
using domain::CommandType;
using domain::parseCommand;
using domain::Role;

void setUp() {}
void tearDown() {}

static void test_simple_commands() {
    TEST_ASSERT_EQUAL(CommandType::Help, parseCommand("help").type);
    TEST_ASSERT_EQUAL(CommandType::Status, parseCommand("status").type);
    TEST_ASSERT_EQUAL(CommandType::Reboot, parseCommand("reboot").type);
    TEST_ASSERT_EQUAL(CommandType::RecStart, parseCommand("rec start").type);
    TEST_ASSERT_EQUAL(CommandType::RecStop, parseCommand("rec stop").type);
    TEST_ASSERT_EQUAL(CommandType::ConfigSave, parseCommand("config save").type);
    TEST_ASSERT_EQUAL(CommandType::ConfigReset, parseCommand("config reset").type);
}

static void test_role_command() {
    Command c = parseCommand("role beacon");
    TEST_ASSERT_EQUAL(CommandType::Role, c.type);
    TEST_ASSERT_EQUAL(Role::Beacon, c.role);
    c = parseCommand("role collector");
    TEST_ASSERT_EQUAL(CommandType::Role, c.type);
    TEST_ASSERT_EQUAL(Role::Collector, c.role);
    c = parseCommand("role banana");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_value", c.errorCode);
}

static void test_id_command_normalises_and_validates() {
    Command c = parseCommand("id 9f3c1a02");
    TEST_ASSERT_EQUAL(CommandType::Id, c.type);
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", c.id.value);

    c = parseCommand("id 9F3C1A0");  // 7 characters
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_id", c.errorCode);

    c = parseCommand("id ZZZZZZZZ");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_id", c.errorCode);

    c = parseCommand("id");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_args", c.errorCode);
}

static void test_power_command() {
    Command c = parseCommand("power 3");
    TEST_ASSERT_EQUAL(CommandType::Power, c.type);
    TEST_ASSERT_EQUAL_INT(3, c.value);
    c = parseCommand("power -6");
    TEST_ASSERT_EQUAL(CommandType::Power, c.type);
    TEST_ASSERT_EQUAL_INT(-6, c.value);
    c = parseCommand("power abc");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_value", c.errorCode);
}

static void test_interval_below_100_is_rejected() {
    Command c = parseCommand("interval 100");
    TEST_ASSERT_EQUAL(CommandType::Interval, c.type);
    TEST_ASSERT_EQUAL_INT(100, c.value);
    c = parseCommand("interval 99");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_value", c.errorCode);
}

static void test_window_below_250_is_rejected() {
    Command c = parseCommand("window 250");
    TEST_ASSERT_EQUAL(CommandType::Window, c.type);
    TEST_ASSERT_EQUAL_INT(250, c.value);
    c = parseCommand("window 249");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_value", c.errorCode);
}

static void test_label_command() {
    Command c = parseCommand("label S1 P3 2.0 4.5");
    TEST_ASSERT_EQUAL(CommandType::Label, c.type);
    TEST_ASSERT_EQUAL_STRING("S1", c.label.session);
    TEST_ASSERT_EQUAL_STRING("P3", c.label.point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, c.label.x);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 4.5f, c.label.y);
}

static void test_label_errors() {
    Command c = parseCommand("label S1 P3 2.0");  // missing y
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_args", c.errorCode);

    c = parseCommand("label S1 P3 abc 4.5");  // not a number
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_label", c.errorCode);

    c = parseCommand("label S,1 P3 2.0 4.5");  // character outside [A-Za-z0-9_-]
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("bad_label", c.errorCode);
}

static void test_unknown_and_empty_lines() {
    Command c = parseCommand("frobnicate");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("unknown_command", c.errorCode);

    c = parseCommand("");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("empty", c.errorCode);

    c = parseCommand("   \r\n");
    TEST_ASSERT_EQUAL(CommandType::Invalid, c.type);
    TEST_ASSERT_EQUAL_STRING("empty", c.errorCode);
}

static void test_tolerates_line_endings_spacing_and_case() {
    TEST_ASSERT_EQUAL(CommandType::Help, parseCommand("help\r\n").type);
    TEST_ASSERT_EQUAL(CommandType::Status, parseCommand("  status  ").type);
    TEST_ASSERT_EQUAL(CommandType::Help, parseCommand("HELP").type);
    Command c = parseCommand("ROLE COLLECTOR");
    TEST_ASSERT_EQUAL(CommandType::Role, c.type);
    TEST_ASSERT_EQUAL(Role::Collector, c.role);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_simple_commands);
    RUN_TEST(test_role_command);
    RUN_TEST(test_id_command_normalises_and_validates);
    RUN_TEST(test_power_command);
    RUN_TEST(test_interval_below_100_is_rejected);
    RUN_TEST(test_window_below_250_is_rejected);
    RUN_TEST(test_label_command);
    RUN_TEST(test_label_errors);
    RUN_TEST(test_unknown_and_empty_lines);
    RUN_TEST(test_tolerates_line_endings_spacing_and_case);
    return UNITY_END();
}
