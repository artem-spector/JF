package com.jflop.server.background;

import com.jflop.server.ServerApp;
import com.jflop.server.admin.data.AgentJVM;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 12/8/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebAppConfiguration
public class TaskTest {

    @Autowired
    private LockIndex lockIndex;

    @Before
    public void cleanup() {
        lockIndex.deleteIndex();
    }

    @Test
    public void testSingleTask() throws Exception {
        String taskName = "one";
        AgentJVM agentJvm = new AgentJVM("acc1", "agent1", "jvm1");
        String lockId = new TaskLockData(taskName, agentJvm).lockId;

        Map<String, Integer> counter = new HashMap<>();
        CountingTask task = createTask(taskName, 2, counter);

        task.start(agentJvm);
        assertNotNull(awaitForLock(3000, task));
        task.releaseLock();
        task.stop(agentJvm);
        assertNull(awaitForLock(1000, task));
        assertEquals(1, counter.get(lockId).intValue());
    }

    @Test
    public void testMultipleTasks() throws Exception {
        int numTasks = 10;
        int numSteps = 7;

        // same task name and agentJvm - simulates multiple nodes competing for the same lock
        String taskName = "two";
        AgentJVM agentJvm = new AgentJVM("acc1", "agent1", "jvm1");
        String lockId = new TaskLockData(taskName, agentJvm).lockId;
        Map<String, Integer> counter = new HashMap<>();

        // 1. create and start tasks
        CountingTask tasks[] = new CountingTask[numTasks];
        for (int i = 0; i < numTasks; i++) tasks[i] = createTask(taskName, 10,counter);
        for (CountingTask task : tasks) task.start(agentJvm);

        // 2. on each step make sure only one task has the lock
        for (int i = 0; i < numSteps; i++) {
            long begin = System.currentTimeMillis();
            String stepStr = "step " + i;
            CountingTask locking = awaitForLock(3000, tasks);
            System.out.println(stepStr + " lock obtained in " + (float)(System.currentTimeMillis() - begin) / 1000 + " sec.");
            assertNotNull(stepStr, locking);

            for (CountingTask task : tasks)
                assertTrue("step " + i + "; isLocking=" + (task == locking) + "; hasLock=" + task.hasLock(),
                        (task == locking && task.hasLock()) || (task != locking && !task.hasLock()));
            assertEquals(stepStr, i + 1, counter.get(lockId).intValue());
            locking.releaseLock();
        }

        // 3. stop the tasks
        for (CountingTask task : tasks) task.stop(agentJvm);
    }

    private CountingTask createTask(String name, int lockTimeout, Map<String, Integer> counter) throws Exception {
        CountingTask res = new CountingTask(name, lockTimeout, counter);
        res.lockIndex = lockIndex;
        res.afterPropertiesSet();
        return res;
    }

    private CountingTask awaitForLock(int timeoutMillis, CountingTask... tasks) {
        long waitUntil = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < waitUntil) {
            for (CountingTask task : tasks) {
                if (task.hasLock()) return task;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        return null;
    }

}

