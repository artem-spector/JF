package com.jflop.server.rest.load;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

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
        String flowStr = "{\"name\":\"m5\",\"duration\":3,\"nested\":[{\"name\":\"m3\",\"duration\":3,\"nested\":[{\"name\":\"m8\",\"duration\":5,\"nested\":[{\"name\":\"m6\",\"duration\":0,\"nested\":[{\"name\":\"m4\",\"duration\":9},{\"name\":\"m1\",\"duration\":7},{\"name\":\"m7\",\"duration\":5},{\"name\":\"m2\",\"duration\":7}]}]}]}]}";
        GeneratedFlow flow = GeneratedFlow.fromString(flowStr);
        System.out.println(flow.toString());

        runLoad(1000, new Object[]{flow, 10f});
    }

    @Test
    public void testGetLoadResult() throws InterruptedException {
        String flowStr = "{\"name\":\"m1\",\"duration\":2,\"nested\":[{\"name\":\"m5\",\"duration\":2},{\"name\":\"m6\",\"duration\":0,\"nested\":[{\"name\":\"m8\",\"duration\":3},{\"name\":\"m2\",\"duration\":1}]}]}";
        GeneratedFlow flow = GeneratedFlow.fromString(flowStr);

        Object[][] flowsAndThroughput = {{flow, 10f}};
        LoadRunner runner = new LoadRunner(flowsAndThroughput);
        runner.startLoad();
        Thread.sleep(500);
        LoadRunner.LoadResult loadRes = runner.getLoadResult(System.currentTimeMillis());
        List<String> problems = LoadRunner.validateResult(loadRes, flowsAndThroughput);
        assertTrue(problems.stream().collect(Collectors.joining("\n", problems.size() + " intermediate flows have problems\n", "")), problems.isEmpty());

        Thread.sleep(500);
        loadRes = runner.stopLoad(10);
        problems = LoadRunner.validateResult(loadRes, flowsAndThroughput);
        assertTrue(problems.stream().collect(Collectors.joining("\n", problems.size() + " final flows have problems\n", "")), problems.isEmpty());

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
        List<String> problems = LoadRunner.validateResult(loadRes, flowsThroughput);
        assertTrue(problems.stream().collect(Collectors.joining("\n", problems.size() + " flows have problems\n", "")), problems.isEmpty());
    }
}
