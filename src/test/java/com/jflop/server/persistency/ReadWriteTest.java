package com.jflop.server.persistency;

import com.jflop.server.ServerApp;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.background.LockIndex;
import com.jflop.server.background.TaskLockData;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.*;
import org.jflop.config.NameUtils;
import org.jflop.snapshot.Flow;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.*;

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

    public static final AgentJVM AGENT_JVM = new AgentJVM("testAcount", "testAgent", "testJVM");

    @Autowired
    private JvmMonitorAnalysis analysis;

    @Autowired
    private LockIndex lockIndex;

    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    @Before
    public void cleanup() throws InterruptedException {
        analysis.stop();
        lockIndex.deleteIndex();
        rawDataIndex.deleteIndex();
        metadataIndex.deleteIndex();
    }

    @Test
    public void testTaskLockData() {
        String taskName = "testTask";
        TaskLockData taskLock = new TaskLockData(taskName, AGENT_JVM);
        PersistentData<TaskLockData> created = lockIndex.createTaskLock(taskLock);
        assertNotNull(created);
        lockIndex.refreshIndex();
        Collection<TaskLockData> vacantLocks = lockIndex.getVacantLocks(taskName);
        assertNotNull(vacantLocks);
        assertEquals(1, vacantLocks.size());
        TaskLockData vacantLock = vacantLocks.iterator().next();
        assertEquals(new Date(0), vacantLock.lockedUntil);
    }

    @Test
    public void testFlowOccurrence() {
        Flow flow = generateFlow(30);

        Collection<AgentData> dataList = new ArrayList<>();
        FlowOccurrenceData occ = new AgentDataFactory(AGENT_JVM, new Date(), rawDataIndex.getDocTypes()).createInstance(FlowOccurrenceData.class);
        occ.init(1f, flow);
        dataList.add(occ);
        rawDataIndex.addRawData(dataList);
        rawDataIndex.refreshIndex();

        List<Metadata> metadataList = new ArrayList<>();
        FlowMetadata meta = new AgentDataFactory(AGENT_JVM, new Date(), metadataIndex.getDocTypes()).createInstance(FlowMetadata.class);
        meta.init(flow, new ArrayList<>());
        metadataList.add(meta);
        metadataIndex.addMetadata(metadataList);
        metadataIndex.refreshIndex();

        Map<FlowMetadata, List<FlowOccurrenceData>> found = rawDataIndex.getOccurrencesAndMetadata(AGENT_JVM, FlowOccurrenceData.class, FlowMetadata.class, new Date(0), new Date());
        assertEquals(1, found.size());
    }

    private Flow generateFlow(int depth) {
        Flow flow = new Flow("AClass", "AClass.java", "aMethod", NameUtils.getMethodDescriptor("() int"), "10");
        if (depth > 1) {
            flow.addNestedCall(generateFlow(depth - 1));
        }
        flow.end("20");
        return flow;
    }
}
