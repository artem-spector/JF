package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for a background processing task.
 * Each agent JVMs has its own task instance that is executed in a separate thread.
 * <p/>
 * This class implements DB synchronization, so that only one node in a cluster executes the task step at any moment.
 * Tasks are explicitly started and stopped on some node, and it affects the task execution that may be on a different node.
 *
 * @author artem on 12/7/16.
 */
public abstract class BackgroundTask implements InitializingBean, DisposableBean {

    private static final Logger logger = Logger.getLogger(BackgroundTask.class.getName());

    private static final int REFRESH_THRESHOLD_SEC = 2;

    private String taskName;
    private long lockTimeoutMillis;
    private long sleepIntervalMillis;

    private ExecutorService threadPool;

    @Autowired
    LockIndex lockIndex;

    private Thread syncThread;
    private boolean stopSyncThread;

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
        start();
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    void start() {
        syncThread = new Thread("jf-task-sync-" + taskName) {
            @Override
            public void run() {
                while (!stopSyncThread) {
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
        };
        syncThread.start();
    }

    public synchronized void stop() throws InterruptedException {
        stopSyncThread = true;
        if (syncThread != null) {
            syncThread.join();
            syncThread = null;
        }
    }

    private void processLock(TaskLockData lock) {
        threadPool.submit(() -> {
            if (lockIndex.obtainLock(lock, System.currentTimeMillis() + lockTimeoutMillis)) {
                try {
                    step(lock, new Date(System.currentTimeMillis() - REFRESH_THRESHOLD_SEC * 1000));
                } catch (Throwable e) {
                    logger.log(Level.SEVERE, "Background task's step failed.", e);
                }
                lockIndex.releaseLock(lock);
            }
        });
    }

    public void createJvmTask(AgentJVM key) {
        lockIndex.createTaskLock(new TaskLockData(taskName, key));
    }

    public void removeJvmTask(AgentJVM key) {
        lockIndex.deleteTaskLock(new TaskLockData(taskName, key));
    }

    public abstract void step(TaskLockData lock, Date refreshThreshold);
}
