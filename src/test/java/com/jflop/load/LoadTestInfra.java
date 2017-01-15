package com.jflop.load;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO: Document!
 *
 * @author artem on 15/01/2017.
 */
public class LoadTestInfra extends LoadTestBase {

    public LoadTestInfra() {
        super("LoadTestInfra");
    }

    @Test
    public void testStartLoadClient() throws Exception {
        String agentName = "reusableAgent";
        int numIterations = 3;

        for (int i = 0; i < numIterations; i++) {
            startClient(agentName);
            assertNotNull(currentJvm);
            logger.info("Started JVM for agent " + agentName + "->" + currentJvm.jvmId);
            Thread.sleep(2000);
            stopClient();
        }
    }

    @Test
    public void testSetLoad() throws Exception {
        startClient("loadAgent");

        int numFlows = 3;
        int minThroughput = 10;
        int maxThroughput = 100;
        int maxDepth = 4;
        int maxLength = 4;
        int maxDuration = 10;
        Object[][] flowsAndThroughput = GeneratedFlow.generateFlowsAndThroughput(numFlows, maxDepth, maxLength, maxDuration, minThroughput, maxThroughput);

        boolean ok = loadRunnerProxy.setFlows(flowsAndThroughput);
        assertTrue(ok);
        ok = loadRunnerProxy.startLoad();
        assertTrue(ok);
        Thread.sleep(2000);
        Map<String, List<Object>> expectedFiredExecutedDuration = loadRunnerProxy.stopLoad(3);
        assertNotNull(expectedFiredExecutedDuration);

        for (Object[] pair : flowsAndThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            float expectedThroughput = (float) pair[1];
            String flowId = flow.getId();
            List<Object> res = (List<Object>) expectedFiredExecutedDuration.get(flowId);
            int expectedCount = (int) res.get(0);
            int firedCount = (int) res.get(1);
            int executedCount = (int) res.get(2);
            int duration = (int) res.get(3);

            assertEquals(expectedCount, executedCount);
        }
    }

}
