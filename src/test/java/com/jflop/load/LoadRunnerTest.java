package com.jflop.load;

import org.junit.Test;

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
        GeneratedFlow f1 = (GeneratedFlow) GeneratedFlow.generateFlowsAndThroughput(1, 4, 4, 0, 5, 10, 10)[0][0];
        runLoad(500, new Object[]{f1, 10f});
        runLoad(900, new Object[]{f1, 100f});
        runLoad(1100, new Object[]{f1, 1000f});
        runLoad(1200, new Object[]{f1, 1500f});
        runLoad(1300, new Object[]{f1, 2000f});

        f1 = (GeneratedFlow) GeneratedFlow.generateFlowsAndThroughput(1, 4, 4, 0, 5, 15, 15)[0][0];
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
        int minDuration = 10;
        int maxDuration = 20;
        runLoad(1000, GeneratedFlow.generateFlowsAndThroughput(numFlows, maxDepth, maxLength, minDuration, maxDuration, minThroughput, maxThroughput));
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
        LoadRunner.LoadResult loadRes = runner.stopLoad(10);

        int numThreads = runner.getNumThreads();
        for (Object[] pair : flowsThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            String flowId = flow.getId();
            LoadRunner.FlowStats stats = loadRes.flows.get(flowId);
            System.out.println(flowId + " in " + numThreads + " threads: expected=" + stats.expected + "; fired=" + stats.fired + "; executed=" + stats.executed
                    + "\n\t duration: expected=" + flow.getExpectedDurationMillis() + "; actual=" + stats.averageDuration);
            assertEquals("Problematic flow:\n" + flow.toString(), stats.expected, stats.executed, 1);
        }
    }
}
