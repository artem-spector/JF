package com.jflop.server.background;

import com.jflop.server.ServerApp;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.ValuePair;
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

    @Autowired
    private JvmMonitorAnalysis analysis;

    @Before
    public void cleanup() throws InterruptedException {
        analysis.stop();
        lockIndex.deleteIndex();
    }

    @Test
    public void testCustomState() throws Exception {
        final TaskLockData[] currentLock = {null};
        TestTaskBase task = initTask(new TestTaskBase("customTask", 2, (lock, refreshThreshold) -> {
            currentLock[0] = lock;
        }));

        AgentJVM agentJvm = new AgentJVM("acc0", "agent0", "jvm0");
        task.createJvmTask(agentJvm);

        assertNotNull(awaitForLock(3000, task));
        TaskLockData theLock = currentLock[0];
        assertNotNull(theLock);
        ValuePair state = theLock.getCustomState(ValuePair.class);
        assertNull(state);
        ValuePair<String, String> pair = new ValuePair<>("str1", "str2");
        theLock.setCustomState(pair);
        task.releaseLock();

        assertNotNull(awaitForLock(3000, task));
        theLock = currentLock[0];
        assertNotNull(theLock);
        state = theLock.getCustomState(ValuePair.class);
        assertEquals(pair, state);
        assertNotSame(pair, state);
        task.releaseLock();

        task.removeJvmTask(agentJvm);
        assertNull(awaitForLock(1000, task));
        task.stop();
    }

    @Test
    public void testSingleTask() throws Exception {
        String taskName = "one";
        AgentJVM agentJvm = new AgentJVM("acc1", "agent1", "jvm1");
        String lockId = new TaskLockData(taskName, agentJvm).lockId;

        Map<String, Integer> counter = new HashMap<>();
        CountingTask task = initTask(new CountingTask(taskName, 2, counter));

        task.createJvmTask(agentJvm);
        assertNotNull(awaitForLock(3000, task));
        task.releaseLock();
        task.removeJvmTask(agentJvm);
        assertNull(awaitForLock(1000, task));
        assertEquals(1, counter.get(lockId).intValue());
        task.stop();
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
        for (int i = 0; i < numTasks; i++) tasks[i] = initTask(new CountingTask(taskName, 10, counter));
        for (CountingTask task : tasks) task.createJvmTask(agentJvm);

        // 2. on each step make sure only one task has the lock
        for (int i = 0; i < numSteps; i++) {
            long begin = System.currentTimeMillis();
            String stepStr = "step " + i;
            CountingTask locking = awaitForLock(3000, tasks);
            System.out.println(stepStr + " lock obtained in " + (float) (System.currentTimeMillis() - begin) / 1000 + " sec.");
            assertNotNull(stepStr, locking);

            for (CountingTask task : tasks)
                assertTrue("step " + i + "; isLocking=" + (task == locking) + "; hasLock=" + task.hasLock(),
                        (task == locking && task.hasLock()) || (task != locking && !task.hasLock()));
            assertEquals(stepStr, i + 1, counter.get(lockId).intValue());
            locking.releaseLock();
        }

        // 3. stop the tasks
        System.out.println("stopping " + tasks.length + " tasks...");
        for (CountingTask task : tasks) {
            task.removeJvmTask(agentJvm);
            task.stop();
        }
    }

    private <T extends TestTaskBase> T initTask(T task) throws Exception {
        task.lockIndex = lockIndex;
        task.afterPropertiesSet();
        return task;
    }

    private <T extends TestTaskBase> T awaitForLock(int timeoutMillis, T... tasks) {
        long waitUntil = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < waitUntil) {
            for (T task : tasks) {
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

