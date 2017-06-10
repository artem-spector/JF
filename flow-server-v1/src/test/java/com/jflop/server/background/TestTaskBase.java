package com.jflop.server.background;

import java.util.Date;

/**
 * Background task that can be manually created (not Spring managed).
 *
 * @author artem on 09/01/2017.
 */
public class TestTaskBase extends BackgroundTask {

    private boolean hasLock;
    private TestTaskStep stepImpl;

    protected TestTaskBase(String taskName, int lockTimeoutSec, TestTaskStep stepImpl) {
        super(taskName, lockTimeoutSec, 1, 10);
        this.stepImpl = stepImpl;
    }

    @Override
    public void step(TaskLockData lock, Date refreshThreshold) {
        hasLock = true;
        stepImpl.step(lock, refreshThreshold);
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

    interface TestTaskStep {
        void step(TaskLockData lock, Date refreshThreshold);
    }
}
