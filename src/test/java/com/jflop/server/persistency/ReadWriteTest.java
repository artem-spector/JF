package com.jflop.server.persistency;

import com.jflop.server.ServerApp;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.background.LockIndex;
import com.jflop.server.background.TaskLockData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Collection;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 4/15/17
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebAppConfiguration
public class ReadWriteTest {

    @Autowired
    private JvmMonitorAnalysis analysis;

    @Autowired
    private LockIndex lockIndex;

    @Before
    public void cleanup() throws InterruptedException {
        analysis.stop();
        lockIndex.deleteIndex();
    }

    @Test
    public void testTaskLockData() {
        String taskName = "testTask";
        TaskLockData taskLock = new TaskLockData(taskName, new AgentJVM("testAcount", "testAgent", "testJVM"));
        PersistentData<TaskLockData> created = lockIndex.createTaskLock(taskLock);
        assertNotNull(created);
        lockIndex.refreshIndex();
        Collection<TaskLockData> vacantLocks = lockIndex.getVacantLocks(taskName);
        assertNotNull(vacantLocks);
        assertEquals(1, vacantLocks.size());
        TaskLockData vacantLock = vacantLocks.iterator().next();
        assertEquals(new Date(0), vacantLock.lockedUntil);
    }
}
