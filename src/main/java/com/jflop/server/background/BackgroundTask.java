package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Base class for a background processing task.
 * Each agent JVMs has its own task instance that is executed in a separate thread.
 * <p/>
 * This class implements DB synchronization, so that only one node in a cluster executes the task step at any moment.
 * Tasks are explicitly started and stopped on some node, and it affects the task execution that may be on a different node.
 *
 * @author artem on 12/7/16.
 */
@Component
public abstract class BackgroundTask implements InitializingBean, DisposableBean {

    private String taskName;
    private long lockTimeoutMillis;
    private long sleepIntervalMillis;

    private ExecutorService threadPool;

    @Autowired
    LockIndex lockIndex;

    private boolean destroy;

    protected BackgroundTask(String taskName, int lockTimeoutSec, int sleepIntervalSec, int maxThreads) {
        this.taskName = taskName;
        this.lockTimeoutMillis = lockTimeoutSec * 1000;
        this.sleepIntervalMillis = sleepIntervalSec * 1000;
        threadPool = Executors.newFixedThreadPool(maxThreads, new ThreadFactory() {
            private int count;
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "jf-task-pool-" + taskName + "-" + ++count);
            }
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        new Thread("jf-task-sync-" + taskName) {
            @Override
            public void run() {
                while (!destroy) {
                    try {
                        Thread.sleep(sleepIntervalMillis);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    Collection<TaskLockData> locks = lockIndex.getVacantLocks(taskName);
                    for (TaskLockData lock : locks) {
                        processLock(lock);
                    }
                }
            }
        }.start();
    }

    private void processLock(TaskLockData lock) {
        threadPool.submit(() -> {
            if (lockIndex.obtainLock(lock, System.currentTimeMillis() + lockTimeoutMillis)) {
                step(lock);
                lockIndex.releaseLock(lock);
            }
        });
    }

    @Override
    public void destroy() throws Exception {
        destroy = true;
    }

    public void start(AgentJVM key) {
        lockIndex.createTaskLock(new TaskLockData(taskName, key));
    }

    public void stop(AgentJVM key) {
        lockIndex.deleteTaskLock(new TaskLockData(taskName, key));
    }

    public abstract void step(TaskLockData lock);
}
