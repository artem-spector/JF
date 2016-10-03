package com.jflop.integration;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.take2.admin.AccountIndex;
import com.jflop.server.take2.admin.AgentJVMIndex;
import com.jflop.server.take2.admin.data.AccountData;
import com.jflop.server.take2.admin.data.AgentJVM;
import com.jflop.server.take2.admin.data.AgentJvmState;
import com.jflop.server.take2.admin.data.FeatureCommand;
import com.jflop.server.take2.feature.InstrumentationConfigurationFeature;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringBufferInputStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private InstrumentationConfigurationFeature configurationFeature;

    private AgentJVM agentJVM;

    private MultipleFlowsProducer producer = new MultipleFlowsProducer();
    private boolean stopIt;

    @Before
    public void activateAgent() throws Exception {
        if (adminClient != null) return;

        accountIndex.deleteIndex();
        agentJVMIndex.deleteIndex();

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

        // 1. get configuration and make sure it's empty
        adminClient.submitCommand(agentJVM.agentId, agentJVM.jvmId, featureId, InstrumentationConfigurationFeature.GET_CONFIG, null);
        FeatureCommand command = awaitFeatureResponse(featureId, 10);
        JflopConfiguration conf = new JflopConfiguration(new StringBufferInputStream(command.successText));
        assertTrue(conf.isEmpty());

        // 2. set configuration from a file
        conf = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
        adminClient.submitCommand(agentJVM.agentId, agentJVM.jvmId, featureId, InstrumentationConfigurationFeature.SET_CONFIG, conf.asJson());
        command = awaitFeatureResponse(featureId, 10);
        assertEquals(conf, new JflopConfiguration(new StringBufferInputStream(command.successText)));
    }

    private FeatureCommand awaitFeatureResponse(String featureId, int timeoutSec) throws Exception {
        long begin = System.currentTimeMillis();
        long timeoutMillis = begin + timeoutSec * 1000;

        while (System.currentTimeMillis() < timeoutMillis) {
            PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJVM, false);
            FeatureCommand command = jvmState.source.getCommand(featureId);
            if (command != null && command.respondedAt != null && command.respondedAt.getTime() > begin) {
                System.out.println(getAgentState());
                return command;
            }
            Thread.sleep(300);
        }
        throw new Exception("Feature state not changed in " + timeoutSec + " sec");
    }

    private Map.Entry<String, Map<String, Object>> awaitJvmStateChange(long fromTime, int timeoutSec) throws Exception {
        Map<String, Object> agentState = null;
        try {
            long timoutMillis = System.currentTimeMillis() + timeoutSec * 1000;
            while (System.currentTimeMillis() < timoutMillis) {
                agentState = getAgentState();
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
        } finally {
            System.out.println("-------- agent state -------");
            System.out.println(agentState);
        }
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

    /*
    @After
    public void clearConfiguration() {
        if (adminClient == null) return;

        InstrumentationConfigurationFeature configurationFeature = agent.getFeature(InstrumentationConfigurationFeature.class);
        configurationFeature.setAgentConfiguration(new JflopConfiguration());
        awaitFeatureResponse(configurationFeature, 3000);
    }


        @Test
        public void testSnapshotFeature() throws IOException, InterruptedException {
            // 1. instrument multiple flows producer
            JflopConfiguration configuration = new JflopConfiguration(getClass().getClassLoader().getResourceAsStream("multipleFlowsProducer.instrumentation.properties"));
            InstrumentationConfigurationFeature configFeature = agent.getFeature(InstrumentationConfigurationFeature.class);
            configFeature.setAgentConfiguration(configuration);
            awaitFeatureResponse(configFeature, 3000);
            assertNull(configFeature.getError());

            // 2. take snapshot without load and make sure there are no flows
            SnapshotFeature snapshotFeature = agent.getFeature(SnapshotFeature.class);
            snapshotFeature.takeSnapshot(2);
            awaitFeatureResponse(snapshotFeature, 5000);
            assertNull(snapshotFeature.getError());
            Snapshot snapshot = snapshotFeature.getLastSnapshot();
            assertTrue(snapshot.getFlowMap().isEmpty());

            // 3. take snapshot under load and make sure all the flows are recorded
            startLoad(2);
            snapshotFeature.takeSnapshot(2);
            awaitFeatureResponse(snapshotFeature, 5000);
            assertNull(snapshotFeature.getError());
            snapshot = snapshotFeature.getLastSnapshot();
            System.out.println(snapshot.format(0, 0));
            assertEquals(2, snapshot.getFlowMap().size());
        }

        private void awaitFeatureResponse(Feature feature, long timeoutMillis) {
            assertNotNull(feature.getProgress());
            long interval = Math.max(100, timeoutMillis / 10);
            long timeout = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < timeout) {
                if (feature.getProgress() == null) return;
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            fail("Feature " + feature.name + " has not respond after " + timeoutMillis + "ms.");
        }

        private void startLoad(int numThreads) {
            stopIt = false;
            Thread[] threads = new Thread[numThreads];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread("ProcessingThread_" + i) {
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
                threads[i].start();
            }
        }

        private void stopLoad() {
            stopIt = true;
        }
    */


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
