package com.jflop.integration;

import com.jflop.HttpTestClient;
import com.jflop.TestUtil;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.AccountData;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.AgentJvmState;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.JvmMonitorFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.RawDataIndex;
import com.jflop.server.runtime.data.ThreadMetadata;
import com.sample.MultipleFlowsProducer;
import org.elasticsearch.index.query.QueryBuilders;
import org.jflop.config.JflopConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Downloads agent and dynamically loads it into the current process.
 *
 * @author artem
 *         Date: 7/9/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebIntegrationTest()
public class IntegrationTest {

    private static final String AGENT_NAME = "testAgent";

    private static AdminClient adminClient;

    private static AgentJVM agentJVM;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private RawDataIndex rawDataIndex;

    @Autowired
    private MetadataIndex metadataIndex;

    @Autowired
    private Collection<IndexTemplate> allIndexes;

    @Autowired
    private InstrumentationConfigurationFeature configurationFeature;

    @Autowired
    private SnapshotFeature snapshotFeature;

    private MultipleFlowsProducer producer = new MultipleFlowsProducer();
    private boolean stopIt;
    private Thread[] loadThreads;

    @Before
    public void activateAgent() throws Exception {
        TestUtil.reset();
        if (adminClient != null) return;

        for (IndexTemplate index : allIndexes) index.deleteIndex();

        HttpTestClient client = new HttpTestClient("http://localhost:8080");
        adminClient = new AdminClient(client, "testAccount");
        String agentId = adminClient.createAgent(AGENT_NAME);
        accountIndex.refreshIndex();
        PersistentData<AccountData> account = accountIndex.findSingle(QueryBuilders.termQuery("accountName", "testAccount"), AccountData.class);
        String accountId = account.id;

        byte[] bytes = adminClient.downloadAgent(agentId);
        File file = new File("target/jflop-agent-test.jar");
        FileOutputStream out = new FileOutputStream(file);
        out.write(bytes);
        out.close();
        loadAgent(file.getPath());

        String jvmId = awaitJvmStateChange(System.currentTimeMillis(), 3).getKey();
        agentJVM = new AgentJVM(accountId, agentId, jvmId);
    }

    @Test
    public void testConfigurationFeature() throws Exception {
        String featureId = InstrumentationConfigurationFeature.FEATURE_ID;

        // 1. submit empty configuration
        adminClient.submitCommand(agentJVM, featureId, InstrumentationConfigurationFeature.SET_CONFIG, configurationAsText(new JflopConfiguration()));
        FeatureCommand command = awaitFeatureResponse(featureId, System.currentTimeMillis(), 10);
        System.out.println(command.successText);

        // 2. get configuration and make sure it's empty
        adminClient.submitCommand(agentJVM, featureId, InstrumentationConfigurationFeature.GET_CONFIG, null);
        command = awaitFeatureResponse(featureId, System.currentTimeMillis(), 10);
        JflopConfiguration conf = new JflopConfiguration(new ByteArrayInputStream(command.successText.getBytes()));
        assertTrue(conf.isEmpty());

        // 3. set configuration from a file
        conf = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
        adminClient.submitCommand(agentJVM, featureId, InstrumentationConfigurationFeature.SET_CONFIG, configurationAsText(conf));
        command = awaitFeatureResponse(featureId, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
        assertEquals(conf, new JflopConfiguration(new ByteArrayInputStream(command.successText.getBytes())));
    }

    @Test
    public void testSnapshotFeature() throws Exception {
        // 1. instrument multiple flows producer
        JflopConfiguration conf = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
        adminClient.submitCommand(agentJVM, InstrumentationConfigurationFeature.FEATURE_ID, InstrumentationConfigurationFeature.SET_CONFIG, configurationAsText(conf));
        FeatureCommand command = awaitFeatureResponse(InstrumentationConfigurationFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        assertNull(command.errorText);

        // 2. take snapshot without load and make sure there are no flows
        Map<String, Object> param = new HashMap<>();
        param.put("durationSec", "2");
        adminClient.submitCommand(agentJVM, SnapshotFeature.FEATURE_ID, SnapshotFeature.TAKE_SNAPSHOT, param);
        command = awaitFeatureResponse(SnapshotFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println("progress: " + command.progressPercent);
        assertTrue(command.progressPercent >= 50);
        command = awaitFeatureResponse(SnapshotFeature.FEATURE_ID, command.respondedAt.getTime(), 10);
        System.out.println(command.successText);
        assertTrue(command.successText.contains("contains no flows."));


        // 3. take snapshot under load and make sure all the flows are recorded
        startLoad(2);
        adminClient.submitCommand(agentJVM, SnapshotFeature.FEATURE_ID, SnapshotFeature.TAKE_SNAPSHOT, param);
        command = awaitFeatureResponse(SnapshotFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println("progress: " + command.progressPercent);
        assertTrue(command.progressPercent >= 50);
        command = awaitFeatureResponse(SnapshotFeature.FEATURE_ID, command.respondedAt.getTime(), 10);
        System.out.println(command.successText);
        assertTrue(command.successText.contains("2 distinct flows"));

        stopLoad();
    }

    @Test
    public void testJvmMonitorFeature() throws Exception {
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        FeatureCommand command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
        assertTrue(command.successText.contains("process CPU load:"));

        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
        long submitted = System.currentTimeMillis();
        command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, submitted, 10);
        System.out.println(command.successText);
        if (!command.successText.contains("OK")) {
            // the first response may come before the command was received by the client, so wait for the next change
            command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, command.respondedAt.getTime(), 10);
        }
        assertTrue(command.successText.contains("OK"));
    }

    @Test
    public void testThreadDumpMetadata() throws Exception {
        // 1. no flows in the beginning
        List<ThreadMetadata> existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, new Date(0L), 1000);
        assertEquals(0, existing.size());

        // 2. enable monitor feature, and make sure there are some flows detected, and all have different stack traces
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        FeatureCommand command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);

        metadataIndex.refreshIndex();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, new Date(0L), 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue("No thread dump metadata found for accountId " + agentJVM.accountId, existing.size() > 0);

        // 3. wait for another report and make sure the number of threads was not duplicated
        command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
        metadataIndex.refreshIndex();
        int oldSize = existing.size();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, new Date(0L), 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue(oldSize < 2 * existing.size());

        // 4. turn on load and make sure there is at least 1 new thread dump
        // the flow of initializing user cache happens in the beginning of a thread and is unlikely to be caught by the thread dump
        System.out.println("start load..");
        startLoad(5);

        for (int i = 0; i < 1; i++)
            command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
        metadataIndex.refreshIndex();
        oldSize = existing.size();
        existing = metadataIndex.findMetadata(agentJVM, ThreadMetadata.class, new Date(0L), 1000);
        System.out.println("Number of thread dumps: " + existing.size());
        assertTrue(existing.size() >= oldSize + 1);

        System.out.println("stop load..");
        stopLoad();
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
    }

    @Test
    public void testThreadAnalysis() throws Exception {
        // start load and monitoring
        startLoad(5);
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.ENABLE, null);
        FeatureCommand command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
        Thread.sleep(3000); // let collect some thread dumps

        long begin = System.currentTimeMillis();
        boolean snapshotTaken = false;
        String snapshotText = null;
        int timeoutSec = 15;
        while (System.currentTimeMillis() - begin < timeoutSec * 1000 && !snapshotTaken) {
            System.out.print(".");
            Thread.sleep(1000);
            snapshotText = snapshotFeature.getLastSnapshot(agentJVM);
            snapshotTaken = snapshotText != null && !snapshotText.isEmpty();
        }

        System.out.println(snapshotText);
        assertTrue("Snapshot not taken in " + timeoutSec + " sec", snapshotTaken);

        // stop load and monitoring
        stopLoad();
        adminClient.submitCommand(agentJVM, JvmMonitorFeature.FEATURE_ID, JvmMonitorFeature.DISABLE, null);
        command = awaitFeatureResponse(JvmMonitorFeature.FEATURE_ID, System.currentTimeMillis(), 10);
        System.out.println(command.successText);
    }

    private String configurationAsText(JflopConfiguration configuration) throws IOException {
        StringWriter writer = new StringWriter();
        configuration.toProperties().store(writer, null);
        return writer.toString();
    }

    private FeatureCommand awaitFeatureResponse(String featureId, long fromTime, int timeoutSec) throws Exception {
        long timeoutMillis = fromTime + timeoutSec * 1000;

        while (System.currentTimeMillis() < timeoutMillis) {
            PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJVM, false);
            FeatureCommand command = jvmState.source.getCommand(featureId);
            if (command != null && command.respondedAt != null && command.respondedAt.getTime() > fromTime) {
                return command;
            }
            Thread.sleep(300);
        }
        throw new Exception("Feature state not changed in " + timeoutSec + " sec");
    }

    private Map.Entry<String, Map<String, Object>> awaitJvmStateChange(long fromTime, int timeoutSec) throws Exception {
        long timoutMillis = System.currentTimeMillis() + timeoutSec * 1000;
        while (System.currentTimeMillis() < timoutMillis) {
            Map<String, Object> agentState = getAgentState();
            Map<String, Map<String, Object>> jvms = agentState == null ? null : (Map<String, Map<String, Object>>) agentState.get("jvms");
            if (jvms != null && !jvms.isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> entry : jvms.entrySet()) {
                    Long lastReported = (Long) entry.getValue().get("lastReportedAt");
                    if (lastReported != null && lastReported >= fromTime)
                        return entry;
                }
            }
            Thread.sleep(300);
        }
        throw new Exception("JVM state not changed in " + timeoutSec + " sec");
    }

    private Map<String, Object> getAgentState() throws Exception {
        List<Map<String, Object>> agents = adminClient.getAgentsJson();
        for (Map<String, Object> agent : agents) {
            if (AGENT_NAME.equals(agent.get("agentName"))) {
                return agent;
            }
        }
        return null;
    }

    private void startLoad(int numThreads) {
        stopIt = false;
        loadThreads = new Thread[numThreads];
        for (int i = 0; i < loadThreads.length; i++) {
            loadThreads[i] = new Thread("ProcessingThread_" + i) {
                public void run() {
                    for (int i = 1; !stopIt; i++) {
                        String user = "usr" + i;
                        for (int j = 0; j < 20; j++) {
                            try {
                                producer.serve(user);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            };
            loadThreads[i].start();
        }
    }

    private void stopLoad() {
        stopIt = true;
        if (loadThreads == null) return;

        for (Thread thread : loadThreads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        for (Thread thread : loadThreads) {
            if (thread.isAlive())
                fail("Thread " + thread.getName() + " is alive after 5 sec");
        }

        loadThreads = null;
    }


    private void loadAgent(String path) throws Exception {
        String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
        int p = nameOfRunningVM.indexOf('@');
        String pid = nameOfRunningVM.substring(0, p);

        try {
            com.sun.tools.attach.VirtualMachine vm = com.sun.tools.attach.VirtualMachine.attach(pid);
            vm.loadAgent(path, "");
            vm.detach();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
