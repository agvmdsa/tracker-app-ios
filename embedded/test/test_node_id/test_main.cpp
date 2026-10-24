// Node ID derivation test (added with T025: the supervisor derives the 6-hex node ID from the
// chip's unique identifier unless it is overridden; data-model.md, DeviceConfiguration).
#include <string.h>
#include <unity.h>

#include "beacon_id.h"
#include "node_id.h"

void setUp() {}
void tearDown() {}

static void test_uses_the_low_three_bytes_in_uppercase_hex() {
    char id[7];
    domain::nodeIdFromChipId(0x112233445566ULL, id);
    TEST_ASSERT_EQUAL_STRING("445566", id);
}

static void test_keeps_leading_zeros() {
    char id[7];
    domain::nodeIdFromChipId(0x000001ULL, id);
    TEST_ASSERT_EQUAL_STRING("000001", id);
}

static void test_ignores_the_high_bits() {
    char id[7];
    domain::nodeIdFromChipId(0xFFFFFFFFFFA1B2C3ULL, id);
    TEST_ASSERT_EQUAL_STRING("A1B2C3", id);
}

static void test_result_is_always_six_hex_characters() {
    char id[7];
    domain::nodeIdFromChipId(0xDEADBEEFCAFEULL, id);
    TEST_ASSERT_EQUAL_UINT32(6, strlen(id));
    TEST_ASSERT_TRUE(domain::isHexOfLength(id, 6));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_uses_the_low_three_bytes_in_uppercase_hex);
    RUN_TEST(test_keeps_leading_zeros);
    RUN_TEST(test_ignores_the_high_bits);
    RUN_TEST(test_result_is_always_six_hex_characters);
    return UNITY_END();
}
