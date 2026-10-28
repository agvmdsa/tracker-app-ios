// Smoke test: proves the host-side Unity environment runs (task T009).
#include <unity.h>

void setUp() {}
void tearDown() {}

static void test_unity_runs_on_the_host() {
    TEST_ASSERT_EQUAL_INT(4, 2 + 2);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_unity_runs_on_the_host);
    return UNITY_END();
}
