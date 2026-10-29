// Wi-Fi and upload reporting on the host: which link changes and upload results become an `E` line,
// and the `# status net` text. The board side (HttpFingerprintUploader) only reads WiFi.status()
// and the HTTP code and hands them here.
#include <string.h>
#include <unity.h>

#include "wifi_status.h"

using namespace domain;

void setUp() {}
void tearDown() {}

static void test_connecting_after_boot_reports_nothing_yet() {
    WifiStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_FALSE(tracker.update(WifiLink::Connecting, "", event));
    TEST_ASSERT_EQUAL(WifiLink::Connecting, tracker.link());
}

static void test_connecting_reports_the_ip_once() {
    WifiStatusTracker tracker;
    NetworkEvent event;
    tracker.update(WifiLink::Connecting, "", event);

    TEST_ASSERT_TRUE(tracker.update(WifiLink::Connected, "192.168.0.23", event));
    TEST_ASSERT_EQUAL_STRING("info", event.level);
    TEST_ASSERT_EQUAL_STRING("wifi_connected", event.code);
    TEST_ASSERT_EQUAL_STRING("ip=192.168.0.23", event.message);

    TEST_ASSERT_FALSE(tracker.update(WifiLink::Connected, "192.168.0.23", event));
    TEST_ASSERT_EQUAL_STRING("192.168.0.23", tracker.ip());
}

static void test_a_network_that_is_not_found_says_so_with_the_likely_causes() {
    WifiStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.update(WifiLink::NoNetwork, "", event));
    TEST_ASSERT_EQUAL_STRING("warn", event.level);
    TEST_ASSERT_EQUAL_STRING("wifi_no_network", event.code);
    TEST_ASSERT_NOT_NULL(strstr(event.message, "2.4 GHz"));
}

static void test_a_wrong_password_is_an_error() {
    WifiStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.update(WifiLink::AuthFailed, "", event));
    TEST_ASSERT_EQUAL_STRING("error", event.level);
    TEST_ASSERT_EQUAL_STRING("wifi_auth_failed", event.code);
}

static void test_losing_the_connection_is_reported_and_the_ip_is_forgotten() {
    WifiStatusTracker tracker;
    NetworkEvent event;
    tracker.update(WifiLink::Connected, "10.0.0.5", event);

    TEST_ASSERT_TRUE(tracker.update(WifiLink::Lost, "", event));
    TEST_ASSERT_EQUAL_STRING("warn", event.level);
    TEST_ASSERT_EQUAL_STRING("wifi_lost", event.code);
    TEST_ASSERT_EQUAL_STRING("", tracker.ip());
}

static void test_the_first_successful_upload_is_reported_then_not_again() {
    UploadStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.onBatch(201, 20, event));
    TEST_ASSERT_EQUAL_STRING("info", event.level);
    TEST_ASSERT_EQUAL_STRING("upload_ok", event.code);
    TEST_ASSERT_EQUAL_STRING("http=201 rows=20", event.message);
    TEST_ASSERT_EQUAL_UINT32(20, tracker.uploadedRows());

    TEST_ASSERT_FALSE(tracker.onBatch(201, 20, event));
    TEST_ASSERT_EQUAL_UINT32(40, tracker.uploadedRows());
}

static void test_a_rejected_key_says_to_check_api_key() {
    UploadStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.onBatch(401, 20, event));
    TEST_ASSERT_EQUAL_STRING("warn", event.level);
    TEST_ASSERT_EQUAL_STRING("upload_failed", event.code);
    TEST_ASSERT_NOT_NULL(strstr(event.message, "http=401"));
    TEST_ASSERT_NOT_NULL(strstr(event.message, "API_KEY"));
    TEST_ASSERT_EQUAL_UINT32(1, tracker.failedBatches());
}

static void test_no_connection_to_the_api_is_told_apart_from_an_http_error() {
    UploadStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.onBatch(-1, 20, event));
    TEST_ASSERT_NOT_NULL(strstr(event.message, "http=-1"));
    TEST_ASSERT_NOT_NULL(strstr(event.message, "no connection"));
}

/** While the API stays down every batch fails the same way: one line, not one every 5 s. A
 *  different code, or a recovery, is reported again. */
static void test_repeated_failures_are_reported_once_until_the_result_changes() {
    UploadStatusTracker tracker;
    NetworkEvent event;
    TEST_ASSERT_TRUE(tracker.onBatch(503, 20, event));
    TEST_ASSERT_FALSE(tracker.onBatch(503, 20, event));
    TEST_ASSERT_TRUE(tracker.onBatch(401, 20, event));
    TEST_ASSERT_TRUE(tracker.onBatch(201, 20, event));
    TEST_ASSERT_EQUAL_STRING("upload_ok", event.code);
    TEST_ASSERT_EQUAL_UINT32(3, tracker.failedBatches());
}

static void test_the_status_line_shows_the_link_ip_and_counts() {
    char buf[128];
    TEST_ASSERT_GREATER_THAN_INT(0, formatNetworkStatus(buf, sizeof(buf), WifiLink::Connected, "192.168.0.23", 40, 1, 2));
    TEST_ASSERT_EQUAL_STRING("wifi=connected ip=192.168.0.23 uploaded=40 failed=1 dropped=2", buf);
}

static void test_the_status_line_without_an_ip_shows_a_dash() {
    char buf[128];
    formatNetworkStatus(buf, sizeof(buf), WifiLink::NoNetwork, "", 0, 0, 0);
    TEST_ASSERT_EQUAL_STRING("wifi=no_network ip=- uploaded=0 failed=0 dropped=0", buf);
}

static void test_the_status_line_refuses_rather_than_truncate() {
    char buf[10];
    TEST_ASSERT_EQUAL_INT(-1, formatNetworkStatus(buf, sizeof(buf), WifiLink::Off, "", 0, 0, 0));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_connecting_after_boot_reports_nothing_yet);
    RUN_TEST(test_connecting_reports_the_ip_once);
    RUN_TEST(test_a_network_that_is_not_found_says_so_with_the_likely_causes);
    RUN_TEST(test_a_wrong_password_is_an_error);
    RUN_TEST(test_losing_the_connection_is_reported_and_the_ip_is_forgotten);
    RUN_TEST(test_the_first_successful_upload_is_reported_then_not_again);
    RUN_TEST(test_a_rejected_key_says_to_check_api_key);
    RUN_TEST(test_no_connection_to_the_api_is_told_apart_from_an_http_error);
    RUN_TEST(test_repeated_failures_are_reported_once_until_the_result_changes);
    RUN_TEST(test_the_status_line_shows_the_link_ip_and_counts);
    RUN_TEST(test_the_status_line_without_an_ip_shows_a_dash);
    RUN_TEST(test_the_status_line_refuses_rather_than_truncate);
    return UNITY_END();
}
