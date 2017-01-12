package com.jflop.load;

import com.jflop.HttpTestClient;
import com.jflop.server.ServerApp;
import com.jflop.server.admin.AccountIndex;
import com.jflop.server.admin.AdminClient;
import com.jflop.server.admin.AgentJVMIndex;
import com.jflop.server.admin.data.AccountData;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.AgentJvmState;
import com.jflop.server.admin.data.JFAgent;
import com.jflop.server.persistency.ValuePair;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;

/**
 * TODO: Document!
 *
 * @author artem on 11/01/2017.
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ServerApp.class)
@WebIntegrationTest()

public class LoadAnalysisTest {

    private static final Logger logger = Logger.getLogger(LoadAnalysisTest.class.getName());

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    private boolean isInitialized;
    private AdminClient adminClient;
    private String accountId;

    private Map<String, ValuePair<String, String>> agentName2IdPath;
    private LoadRunnerProcess.Proxy loadRunnerProxy;
    private AgentJVM currentJvm;

    @Before
    public synchronized void initOnce() throws Exception {
        if (!isInitialized) {
            initAccount("LoadTestAccount");
            isInitialized = true;
        }
    }

    @After
    public void stopClient() {
        if (loadRunnerProxy == null) return;
        String jvmStr = currentJvm == null ? "unknown agent JVM" : currentJvm.toString();

        int timeoutSec = 5;
        if (loadRunnerProxy.exit(timeoutSec)) {
            logger.info("Agent JVM has stopped: " + jvmStr);
            loadRunnerProxy = null;
            currentJvm = null;
        } else
            logger.severe("The client process has not stopped in " + timeoutSec + " sec. for " + jvmStr);
    }

    @Test
    public void testStartLoadClient() throws Exception {
        String agentName = "reusableAgent";
        int numIterations = 1;

        for (int i = 0; i < numIterations; i++) {
            startClient(agentName);
            assertNotNull(currentJvm);
            logger.info("Started JVM for agent " + agentName + "->" + currentJvm.jvmId);
            Thread.sleep(2000);
            stopClient();
        }
    }

    private void initAccount(String accountName) throws Exception {
        HttpTestClient client = new HttpTestClient("http://localhost:8080");
        adminClient = new AdminClient(client, accountName);
        accountIndex.refreshIndex();
        accountId = accountIndex.findSingle(QueryBuilders.termQuery("accountName", accountName), AccountData.class).id;

        // delete existing agents
        for (JFAgent agent : adminClient.getAgents()) {
            adminClient.deleteAgent(agent.agentId);
        }
        agentName2IdPath = new HashMap<>();
    }

    private ValuePair<String, String> getOrCreateAgent(String agentName) throws Exception {
        ValuePair<String, String> idPath = agentName2IdPath.get(agentName);
        if (idPath == null) {
            String agentId = adminClient.createAgent(agentName);
            accountIndex.refreshIndex();
            byte[] bytes = adminClient.downloadAgent(agentId);
            logger.info("Downloaded agent for agentId: " + agentId);
            File file = new File("target/jflop-agent-" + agentName + ".jar");
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.close();
            idPath = new ValuePair<>(agentId, file.getAbsolutePath());
            agentName2IdPath.put(agentName, idPath);
            System.out.println("Agent downloaded to path " + idPath.value2);
        }
        return idPath;
    }

    private void startClient(String agentName) throws Exception {
        assert loadRunnerProxy == null;

        ValuePair<String, String> pair = getOrCreateAgent(agentName);
        String agentId = pair.value1;
        String agentPath = pair.value2;
        Map<AgentJVM, Date> jvmsBefore = getAgentJVMsLastReported(agentId);

        loadRunnerProxy = LoadRunnerProcess.start(agentPath);
        Date startedAt = new Date();

        AgentJVM found = null;
        int timeoutSec = 3;
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        while (found == null && System.currentTimeMillis() < border) {
            for (Map.Entry<AgentJVM, Date> entry : getAgentJVMsLastReported(agentId).entrySet()) {
                AgentJVM jvm = entry.getKey();
                if (!jvmsBefore.containsKey(jvm) && entry.getValue().after(startedAt)) {
                    found = jvm;
                    break;
                }
            }
            Thread.sleep(500);
        }

        assertNotNull("Agent JVM not reported in " + timeoutSec + " sec. from the process start.", found);
        logger.info("Agent JVM has started reporting: " + found);
        currentJvm = found;
    }

    private Map<AgentJVM, Date> getAgentJVMsLastReported(String agentId) {
        Map<AgentJVM, Date> res = new HashMap<>();
        for (AgentJvmState jvmState : agentJVMIndex.getAgentJvms(accountId)) {
            if (jvmState.agentJvm.agentId.equals(agentId)) {
                res.put(jvmState.agentJvm, jvmState.lastReportedAt);
            }
        }
        return res;
    }
}
