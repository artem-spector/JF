package com.jflop.server.background;

import java.util.Map;

/**
 * Counts the number of steps for each lock
 *
 * @author artem on 12/8/16.
 */
class CountingTask extends TestTaskBase {

    CountingTask(String taskName, int lockTimeoutSec, Map<String, Integer> counter) {
        super(taskName, lockTimeoutSec, (lock, refreshThreshold) -> {
            Integer num = counter.get(lock.lockId);
            counter.put(lock.lockId, num == null ? 1 : num + 1);
        });
    }

}
