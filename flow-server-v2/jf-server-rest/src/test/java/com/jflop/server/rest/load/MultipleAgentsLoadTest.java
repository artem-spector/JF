package com.jflop.server.rest.load;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.rest.persistency.ValuePair;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 4/16/17
 */
public class MultipleAgentsLoadTest extends LoadTestBase {

    private List<ValuePair<LoadRunnerProcess.Proxy, AgentJVM>> agents;


    public MultipleAgentsLoadTest() {
        super("MultiAgentsTest", true);
    }

    @After
    public void stopAgents() {
        if (agents == null) return;
        for (ValuePair<LoadRunnerProcess.Proxy, AgentJVM> agent : agents) {
            int timeoutSec = 5;
            if (agent.value1.exit(timeoutSec)) {
                logger.info("Agent JVM has stopped: " + agent.value2);
            } else
                logger.severe("The client process has not stopped in " + timeoutSec + " sec. for " + agent.value2);
        }
        agents = null;
    }

    @Test
    public void runOneAgentContinuously() throws Exception {
        startAgents(1);
        while (true) {
            Thread.sleep(3000);
            System.out.print(".");
        }
    }

    @Test
    public void startTwoAgents() throws Exception {
        startAgents(3, 9);
        System.out.println("Sleep for 20 sec...");
        Thread.sleep(20000);
        System.out.println("Done.");
    }

    protected void startAgents(int... numFlows) throws Exception {
        assert agents == null;
        agents = new ArrayList<>();

        int i = 1;
        for (int agentNumFlows : numFlows) {
            String agentName = "agent-" + i++;

            // register agent and get the jar
            logger.info("---------------- Setting up " + agentName);
            ValuePair<String, String> pair = getOrCreateAgent(agentName);
            String agentId = pair.value1;
            String agentPath = pair.value2;

            // start the process and wait it to report
            Map<AgentJVM, Date> jvmsBefore = getAgentJVMsLastReported(agentId);
            LoadRunnerProcess.Proxy proxy = LoadRunnerProcess.start(agentPath);
            Date startedAt = new Date();

            AgentJVM agentJVM = null;
            int timeoutSec = 3;
            long border = System.currentTimeMillis() + timeoutSec * 1000;
            while (agentJVM == null && System.currentTimeMillis() < border) {
                for (Map.Entry<AgentJVM, Date> entry : getAgentJVMsLastReported(agentId).entrySet()) {
                    AgentJVM jvm = entry.getKey();
                    if (!jvmsBefore.containsKey(jvm) && entry.getValue().after(startedAt)) {
                        agentJVM = jvm;
                        break;
                    }
                }
                Thread.sleep(500);
            }

            assertNotNull(agentName + " JVM not reported in " + timeoutSec + " sec. from the process start.", agentJVM);
            logger.info(agentName + " JVM has started reporting: " + agentJVM);
            agents.add(new ValuePair<>(proxy, agentJVM));

            // generate the flows and start load
            boolean ok = proxy.setFlows(generateFlows(agentNumFlows));
            assertTrue(ok);
            ok = proxy.startLoad();
            assertTrue(ok);
            logger.info(agentName + " started load with " + agentNumFlows + " flows");

        }
    }

    protected Object[][] generateFlows(int numFlows) {
        return GeneratedFlow.generateFlowsAndThroughput(numFlows, 4, 4, 20, 200, 3, 30);
    }
}
