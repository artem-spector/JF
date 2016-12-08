package com.jflop.server.background;

import java.util.Map;

/**
 * Background task that can be manually created (not Spring managed).
 * Counts the number of steps for each lock
 *
 * @author artem on 12/8/16.
 */
public class CountingTask extends BackgroundTask {

    private Map<String, Integer> counter;
    private boolean hasLock;

    public CountingTask(String taskName, int lockTimeoutSec, Map<String, Integer> counter) {
        super(taskName, lockTimeoutSec, 1, 10);
        this.counter = counter;
    }

    @Override
    public void step(TaskLockData lock) {
        hasLock = true;
        Integer num = counter.get(lock.lockId);
        counter.put(lock.lockId, num == null ? 1 : num + 1);
        try {
            while (hasLock) Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public boolean hasLock() {
        return hasLock;
    }

    public void releaseLock() {
        assert hasLock;
        hasLock = false;
    }
}
