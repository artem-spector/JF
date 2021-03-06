package com.jflop.server.rest.load;

import com.jflop.server.rest.JfServerRestApp;
import com.jflop.server.rest.admin.AccountIndex;
import com.jflop.server.rest.admin.AdminClient;
import com.jflop.server.rest.admin.AgentJVMIndex;
import com.jflop.server.rest.admin.data.AccountData;
import com.jflop.server.data.AgentJVM;
import com.jflop.server.rest.admin.data.AgentJvmState;
import com.jflop.server.rest.admin.data.JFAgent;
import com.jflop.server.rest.http.HttpTestClient;
import com.jflop.server.rest.persistency.IndexTemplate;
import com.jflop.server.rest.persistency.ValuePair;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 11/01/2017.
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JfServerRestApp.class)
@WebIntegrationTest()

public abstract class LoadTestBase {

    static final Logger logger = Logger.getLogger(LoadTestBase.class.getName());

    protected Object[][] flowsAndThroughput;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    @Autowired
    private List<IndexTemplate> allIndexes;

    private String accountName;
    private boolean deleteAllDataBefore;
    private boolean isInitialized;
    protected AdminClient adminClient;

    private String accountId;
    private Map<String, ValuePair<String, String>> agentName2IdPath;
    protected LoadRunnerProcess.Proxy loadRunnerProxy;
    protected AgentJVM currentJvm;

    protected LoadTestBase(String accountName, boolean deleteAllDataBefore) {
        this.accountName = accountName;
        this.deleteAllDataBefore = deleteAllDataBefore;
    }

    @Before
    public synchronized void initOnce() throws Exception {
        if (!isInitialized) {
            if (deleteAllDataBefore) allIndexes.forEach(IndexTemplate::deleteIndex);
            initAccount(accountName);
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

    protected void startClient(String agentName) throws Exception {
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

    protected void generateFlows(int numFlows, int minThroughput, int maxThroughput, int minDuration, int maxDuration) {
        int maxDepth = 4;
        int maxLength = 4;
        flowsAndThroughput = GeneratedFlow.generateFlowsAndThroughput(numFlows, maxDepth, maxLength, minDuration, maxDuration, minThroughput, maxThroughput);
    }

    protected void startLoad() {
        boolean ok = loadRunnerProxy.setFlows(flowsAndThroughput);
        assertTrue(ok);
        ok = loadRunnerProxy.startLoad();
        assertTrue(ok);
    }

    protected LoadRunner.LoadResult stopLoad() {
        LoadRunner.LoadResult loadResult = loadRunnerProxy.stopLoad(3);
        assertNotNull(loadResult);
        List<String> problems = LoadRunner.validateResult(loadResult, flowsAndThroughput);
        assertTrue(problems.stream().collect(Collectors.joining("\n", problems.size() + " flows have problems\n", "")), problems.isEmpty());

        return loadResult;
    }

    protected LoadRunner.LoadResult getLoadResult() {
        LoadRunner.LoadResult loadResult = loadRunnerProxy.getLoadResult();
        assertNotNull(loadResult);
        List<String> problems = LoadRunner.validateResult(loadResult, flowsAndThroughput);
        assertTrue(problems.stream().collect(Collectors.joining("\n", problems.size() + " flows have problems\n", "")), problems.isEmpty());

        return loadResult;
    }

    private void initAccount(String accountName) throws Exception {
        HttpTestClient client = new HttpTestClient("http://localhost:8080");
        adminClient = new AdminClient(client, accountName);
        List<JFAgent> agents = adminClient.getAgents(); // make sure the account exists
        accountIndex.refreshIndex();
        accountId = accountIndex.findSingle(QueryBuilders.termQuery("accountName", accountName), AccountData.class).id;

        // delete existing agents
        for (JFAgent agent : agents) {
            adminClient.deleteAgent(agent.agentId);
        }
        agentName2IdPath = new HashMap<>();
    }

    protected ValuePair<String, String> getOrCreateAgent(String agentName) throws Exception {
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

    protected Map<AgentJVM, Date> getAgentJVMsLastReported(String agentId) {
        Map<AgentJVM, Date> res = new HashMap<>();
        for (AgentJvmState jvmState : agentJVMIndex.getAgentJvms(accountId)) {
            if (jvmState.agentJvm.agentId.equals(agentId)) {
                res.put(jvmState.agentJvm, jvmState.lastReportedAt);
            }
        }
        return res;
    }
}
