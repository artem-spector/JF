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
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.sample.MultipleFlowsProducer;
import org.elasticsearch.index.query.QueryBuilders;
import org.jflop.config.JflopConfiguration;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Downloads agent and dynamically loads it into the current process.
 *
 * @author artem
 *         Date: 12/17/16
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebIntegrationTest()
public class IntegrationTestBase {

    private static final String AGENT_NAME = "testAgent";

    protected static AdminClient adminClient;

    protected static AgentJVM agentJVM;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private Collection<IndexTemplate> allIndexes;

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

    protected void refreshAll() {
        for (IndexTemplate index : allIndexes) {
            index.refreshIndex();
        }
    }

    protected FeatureCommand awaitFeatureResponse(String featureId, long fromTime, int timeoutSec, CommandValidator waitFor) throws Exception {
        long timeoutMillis = fromTime + timeoutSec * 1000;

        while (System.currentTimeMillis() < timeoutMillis) {
            PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJVM, false);
            FeatureCommand command = jvmState.source.getCommand(featureId);
            if (command != null && command.respondedAt != null && command.respondedAt.getTime() > fromTime
                    && (waitFor == null || waitFor.validateCommand(command))) {
                return command;
            }
            Thread.sleep(300);
        }
        throw new Exception("Feature state not changed in " + timeoutSec + " sec");
    }

    protected void startLoad(int numThreads) {
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

    protected void stopLoad() {
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

    protected String configurationAsText(JflopConfiguration configuration) throws IOException {
        StringWriter writer = new StringWriter();
        configuration.toProperties().store(writer, null);
        return writer.toString();
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

    protected interface CommandValidator {
        boolean validateCommand(FeatureCommand command);
    }

}
