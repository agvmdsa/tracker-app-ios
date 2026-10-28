// IPv6-first, fall-back-to-IPv4 timing for the MQTT connection attempt.
#include <unity.h>

#include "dual_stack_strategy.h"

using namespace domain;

void setUp() {}
void tearDown() {}

static void test_starts_on_v6_when_v6_is_configured() {
    DualStackConnectStrategy s(5000, /*hasV6=*/true);
    s.reset(1000);
    TEST_ASSERT_TRUE(AddressFamily::V6 == s.poll(1000));
}

static void test_stays_on_v6_before_the_timeout() {
    DualStackConnectStrategy s(5000, /*hasV6=*/true);
    s.reset(1000);
    TEST_ASSERT_TRUE(AddressFamily::V6 == s.poll(1000));
    TEST_ASSERT_TRUE(AddressFamily::V6 == s.poll(4000));
    TEST_ASSERT_TRUE(AddressFamily::V6 == s.poll(5999));
}

static void test_falls_back_to_v4_once_the_timeout_elapses() {
    DualStackConnectStrategy s(5000, /*hasV6=*/true);
    s.reset(1000);
    TEST_ASSERT_TRUE(AddressFamily::V4 == s.poll(6000));
    // and stays there, it doesn't bounce back to V6 on its own
    TEST_ASSERT_TRUE(AddressFamily::V4 == s.poll(60000));
}

static void test_a_fresh_reset_tries_v6_again_even_after_a_previous_fallback() {
    DualStackConnectStrategy s(5000, /*hasV6=*/true);
    s.reset(1000);
    s.poll(10000);  // falls back to V4
    TEST_ASSERT_TRUE(AddressFamily::V4 == s.currentFamily());
    s.reset(20000);  // e.g. Wi-Fi reconnected, or the MQTT connection dropped
    TEST_ASSERT_TRUE(AddressFamily::V6 == s.poll(20000));
}

static void test_skips_v6_entirely_when_none_is_configured() {
    DualStackConnectStrategy s(5000, /*hasV6=*/false);
    s.reset(1000);
    TEST_ASSERT_TRUE(AddressFamily::V4 == s.poll(1000));
    TEST_ASSERT_TRUE(AddressFamily::V4 == s.poll(1500));  // never waits for a timeout that won't matter
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_starts_on_v6_when_v6_is_configured);
    RUN_TEST(test_stays_on_v6_before_the_timeout);
    RUN_TEST(test_falls_back_to_v4_once_the_timeout_elapses);
    RUN_TEST(test_a_fresh_reset_tries_v6_again_even_after_a_previous_fallback);
    RUN_TEST(test_skips_v6_entirely_when_none_is_configured);
    return UNITY_END();
}
