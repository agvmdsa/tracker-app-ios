// JSON body for the live-sighting MQTT telemetry (the API's SightingIn shape, ESP32 subset).
#include <string.h>
#include <unity.h>

#include "sighting_payload.h"

using namespace domain;

void setUp() {}
void tearDown() {}

static void test_builds_the_expected_json_object() {
    char buf[128];
    const int n = formatSightingPayload(buf, sizeof(buf), "A1B2C3", "9F3C1A02", -67);
    TEST_ASSERT_GREATER_THAN_INT(0, n);
    TEST_ASSERT_EQUAL_INT((int)strlen(buf), n);
    TEST_ASSERT_EQUAL_STRING("{\"observerID\":\"A1B2C3\",\"anchorID\":\"9F3C1A02\",\"rssi\":-67}", buf);
}

static void test_refuses_rather_than_truncate_when_the_buffer_is_too_small() {
    char buf[10];
    TEST_ASSERT_EQUAL_INT(-1, formatSightingPayload(buf, sizeof(buf), "A1B2C3", "9F3C1A02", -67));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_builds_the_expected_json_object);
    RUN_TEST(test_refuses_rather_than_truncate_when_the_buffer_is_too_small);
    return UNITY_END();
}
