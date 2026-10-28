// T013: CollectionLabel. Constraint (data-model.md), verbatim: "session (<= 16 chars of
// [A-Za-z0-9_-]), point (same), x, y (metres, float, in the session's local frame)".
#include <unity.h>

#include "label.h"

using domain::CollectionLabel;
using domain::makeLabel;

void setUp() {}
void tearDown() {}

static void test_accepts_a_typical_label() {
    CollectionLabel l;
    TEST_ASSERT_TRUE(makeLabel("S1", "P3", 2.0f, 4.5f, l));
    TEST_ASSERT_EQUAL_STRING("S1", l.session);
    TEST_ASSERT_EQUAL_STRING("P3", l.point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, l.x);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 4.5f, l.y);
}

static void test_accepts_the_allowed_characters() {
    CollectionLabel l;
    TEST_ASSERT_TRUE(makeLabel("Ab_c-9", "point_01", 0.0f, 0.0f, l));
}

static void test_accepts_exactly_sixteen_characters() {
    CollectionLabel l;
    TEST_ASSERT_TRUE(makeLabel("0123456789ABCDEF", "P", 0.0f, 0.0f, l));
    TEST_ASSERT_EQUAL_STRING("0123456789ABCDEF", l.session);
}

static void test_rejects_seventeen_characters() {
    CollectionLabel l;
    TEST_ASSERT_FALSE(makeLabel("0123456789ABCDEFG", "P", 0.0f, 0.0f, l));
    TEST_ASSERT_FALSE(makeLabel("S", "0123456789ABCDEFG", 0.0f, 0.0f, l));
}

static void test_rejects_characters_outside_the_set() {
    CollectionLabel l;
    TEST_ASSERT_FALSE(makeLabel("S,1", "P", 0.0f, 0.0f, l));    // comma would break the CSV
    TEST_ASSERT_FALSE(makeLabel("S 1", "P", 0.0f, 0.0f, l));    // space
    TEST_ASSERT_FALSE(makeLabel("S", "P.3", 0.0f, 0.0f, l));    // dot
    TEST_ASSERT_FALSE(makeLabel("S", "P/3", 0.0f, 0.0f, l));    // slash
}

static void test_rejects_empty_and_null() {
    CollectionLabel l;
    TEST_ASSERT_FALSE(makeLabel("", "P", 0.0f, 0.0f, l));
    TEST_ASSERT_FALSE(makeLabel("S", "", 0.0f, 0.0f, l));
    TEST_ASSERT_FALSE(makeLabel(nullptr, "P", 0.0f, 0.0f, l));
    TEST_ASSERT_FALSE(makeLabel("S", nullptr, 0.0f, 0.0f, l));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_accepts_a_typical_label);
    RUN_TEST(test_accepts_the_allowed_characters);
    RUN_TEST(test_accepts_exactly_sixteen_characters);
    RUN_TEST(test_rejects_seventeen_characters);
    RUN_TEST(test_rejects_characters_outside_the_set);
    RUN_TEST(test_rejects_empty_and_null);
    return UNITY_END();
}
