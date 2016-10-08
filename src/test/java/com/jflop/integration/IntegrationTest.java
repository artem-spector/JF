package com.jflop.integration;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.AccountData;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.AgentJvmState;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;
import com.jflop.server.persistency.PersistentData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private InstrumentationConfigurationFeature configurationFeature;

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
