// T012: bounded queue with a drop-newest policy and a drop counter.
// Capacities used later (data-model.md): "Observation queue 128 events; Serial output queue
// 64 lines x 128 B; MQTT publish queue 32 messages x 192 B".
#include <unity.h>

#include "bounded_queue.h"

using domain::BoundedQueue;

void setUp() {}
void tearDown() {}

static void test_new_queue_is_empty_with_the_declared_capacity() {
    BoundedQueue<int, 4> q;
    TEST_ASSERT_EQUAL_UINT32(0, q.size());
    TEST_ASSERT_EQUAL_UINT32(4, q.capacity());
    TEST_ASSERT_EQUAL_UINT32(0, q.dropped());
    int out = 0;
    TEST_ASSERT_FALSE(q.pop(out));
}

static void test_items_come_out_in_fifo_order() {
    BoundedQueue<int, 4> q;
    TEST_ASSERT_TRUE(q.push(10));
    TEST_ASSERT_TRUE(q.push(20));
    TEST_ASSERT_TRUE(q.push(30));
    int out = 0;
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(10, out);
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(20, out);
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(30, out);
    TEST_ASSERT_FALSE(q.pop(out));
}

static void test_full_queue_drops_the_newest_and_counts_it() {
    BoundedQueue<int, 3> q;
    TEST_ASSERT_TRUE(q.push(1));
    TEST_ASSERT_TRUE(q.push(2));
    TEST_ASSERT_TRUE(q.push(3));
    TEST_ASSERT_FALSE(q.push(4));  // dropped
    TEST_ASSERT_FALSE(q.push(5));  // dropped
    TEST_ASSERT_EQUAL_UINT32(3, q.size());
    TEST_ASSERT_EQUAL_UINT32(2, q.dropped());

    int out = 0;
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(1, out);  // the oldest are kept, the newest were dropped
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(2, out);
    TEST_ASSERT_TRUE(q.pop(out));
    TEST_ASSERT_EQUAL_INT(3, out);
}

static void test_queue_wraps_around_after_pops() {
    BoundedQueue<int, 3> q;
    int out = 0;
    for (int round = 0; round < 10; ++round) {
        TEST_ASSERT_TRUE(q.push(round));
        TEST_ASSERT_TRUE(q.push(round + 100));
        TEST_ASSERT_TRUE(q.pop(out));
        TEST_ASSERT_EQUAL_INT(round, out);
        TEST_ASSERT_TRUE(q.pop(out));
        TEST_ASSERT_EQUAL_INT(round + 100, out);
    }
    TEST_ASSERT_EQUAL_UINT32(0, q.size());
    TEST_ASSERT_EQUAL_UINT32(0, q.dropped());
}

static void test_declared_capacities_compile_and_hold() {
    BoundedQueue<int, 128> observations;
    for (int i = 0; i < 128; ++i) TEST_ASSERT_TRUE(observations.push(i));
    TEST_ASSERT_FALSE(observations.push(128));
    TEST_ASSERT_EQUAL_UINT32(1, observations.dropped());
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_new_queue_is_empty_with_the_declared_capacity);
    RUN_TEST(test_items_come_out_in_fifo_order);
    RUN_TEST(test_full_queue_drops_the_newest_and_counts_it);
    RUN_TEST(test_queue_wraps_around_after_pops);
    RUN_TEST(test_declared_capacities_compile_and_hold);
    return UNITY_END();
}
