package com.jflop.load;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests the load runner infrastructure
 *
 * @author artem on 10/01/2017.
 */
public class LoadRunnerTest {

    @Test
    public void testSingleFlow() {
        // short flow with different throughputs
        GeneratedFlow f1 = (GeneratedFlow) GeneratedFlow.generateFlowsAndThroughput(1, 4, 4, 5, 10, 10)[0][0];
        runLoad(500, new Object[]{f1, 10f});
        runLoad(900, new Object[]{f1, 100f});
        runLoad(1100, new Object[]{f1, 1000f});
        runLoad(1200, new Object[]{f1, 1500f});
        runLoad(1300, new Object[]{f1, 2000f});

        f1 = (GeneratedFlow) GeneratedFlow.generateFlowsAndThroughput(1, 4, 4, 5, 15, 15)[0][0];
        runLoad(500, new Object[]{f1, 10f});
        runLoad(900, new Object[]{f1, 100f});
        runLoad(1200, new Object[]{f1, 1000f});
    }

    @Test
    public void testMultipleFlows() {
        int numFlows = 20;
        int minThroughput = 10;
        int maxThroughput = 100;
        int maxDepth = 4;
        int maxLength = 4;
        int maxDuration = 10;
        runLoad(1000, GeneratedFlow.generateFlowsAndThroughput(numFlows, maxDepth, maxLength, maxDuration, minThroughput, maxThroughput));
    }

    @Test
    public void testProblematicFlows() {
        GeneratedFlow flow = GeneratedFlow.fromString("{\"name\":\"m2\",\"duration\":5,\"nested\":[{\"name\":\"m4\",\"duration\":0,\"nested\":[{\"name\":\"m6\",\"duration\":0,\"nested\":[{\"name\":\"m3\",\"duration\":0,\"nested\":[{\"name\":\"m7\",\"duration\":0}]}]},{\"name\":\"m5\",\"duration\":0,\"nested\":[{\"name\":\"m8\",\"duration\":0},{\"name\":\"m1\",\"duration\":0,\"nested\":[]}]}]}]}");
        System.out.println(flow.toString());

        runLoad(1000, new Object[]{flow, 1100f});
    }

    private void runLoad(long testDurationMillis, Object[]... flowsThroughput) {
        LoadRunner runner = new LoadRunner(flowsThroughput);
        runner.startLoad();
        try {
            Thread.sleep(testDurationMillis);
        } catch (InterruptedException e) {
            // ignore
        }
        Map<String, Object[]> loadRes = runner.stopLoad(10);

        int numThreads = runner.getNumThreads();
        for (Object[] pair : flowsThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            String flowId = flow.getId();
            Object[] expectedFiredExecutedDuration = loadRes.get(flowId);
            int expected = (int) expectedFiredExecutedDuration[0];
            int fired = (int) expectedFiredExecutedDuration[1];
            int executed = (int) expectedFiredExecutedDuration[2];
            long duration = (long) expectedFiredExecutedDuration[3];
            float avgDuration = (float) duration / executed;
            System.out.println(flowId + " in " + numThreads + " threads: expected=" + expected + "; fired=" + fired + "; executed=" + executed
                    + "\n\t duration: expected=" + flow.getExpectedDurationMillis() + "; actual=" + avgDuration);
            assertEquals("Problematic flow:\n" + flow.toString(), expected, executed, 1);
        }
    }
}
