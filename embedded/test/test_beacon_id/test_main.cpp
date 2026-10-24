// T010: BeaconId validation. Constraint (data-model.md): matches ^[0-9A-Fa-f]{8}$; stored and
// emitted uppercase.
#include <unity.h>

#include "beacon_id.h"

using domain::BeaconId;
using domain::parseBeaconId;

void setUp() {}
void tearDown() {}

static void test_accepts_uppercase_id() {
    BeaconId id;
    TEST_ASSERT_TRUE(parseBeaconId("9F3C1A02", id));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", id.value);
}

static void test_accepts_lowercase_and_stores_uppercase() {
    BeaconId id;
    TEST_ASSERT_TRUE(parseBeaconId("9f3c1a02", id));
    TEST_ASSERT_EQUAL_STRING("9F3C1A02", id.value);
}

static void test_accepts_mixed_case() {
    BeaconId id;
    TEST_ASSERT_TRUE(parseBeaconId("aBcD0123", id));
    TEST_ASSERT_EQUAL_STRING("ABCD0123", id.value);
}

static void test_rejects_seven_characters() {
    BeaconId id;
    TEST_ASSERT_FALSE(parseBeaconId("9F3C1A0", id));
}

static void test_rejects_nine_characters() {
    BeaconId id;
    TEST_ASSERT_FALSE(parseBeaconId("9F3C1A023", id));
}

static void test_rejects_non_hex_characters() {
    BeaconId id;
    TEST_ASSERT_FALSE(parseBeaconId("ZZZZZZZZ", id));
    TEST_ASSERT_FALSE(parseBeaconId("9F3C1A0G", id));
}

static void test_rejects_surrounding_whitespace() {
    BeaconId id;
    TEST_ASSERT_FALSE(parseBeaconId("9F3C1A02 ", id));
    TEST_ASSERT_FALSE(parseBeaconId(" 9F3C1A02", id));
}

static void test_rejects_empty_and_null() {
    BeaconId id;
    TEST_ASSERT_FALSE(parseBeaconId("", id));
    TEST_ASSERT_FALSE(parseBeaconId(nullptr, id));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_accepts_uppercase_id);
    RUN_TEST(test_accepts_lowercase_and_stores_uppercase);
    RUN_TEST(test_accepts_mixed_case);
    RUN_TEST(test_rejects_seven_characters);
    RUN_TEST(test_rejects_nine_characters);
    RUN_TEST(test_rejects_non_hex_characters);
    RUN_TEST(test_rejects_surrounding_whitespace);
    RUN_TEST(test_rejects_empty_and_null);
    return UNITY_END();
}
