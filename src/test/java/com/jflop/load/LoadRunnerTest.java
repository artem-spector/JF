package com.jflop.load;

import com.jflop.server.persistency.ValuePair;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Tests the load runner infrastructure
 *
 * @author artem on 10/01/2017.
 */
public class LoadRunnerTest {

    private static final int OVERHEAD_MILLIS = 10;

    @Test
    public void testSingleFlow() {
        FlowOne f1 = new FlowOne("one", 3);
        runLoad(500, new Object[]{f1, 10f});
        runLoad(900, new Object[]{f1, 100f});
        runLoad(1100, new Object[]{f1, 1000f});
        runLoad(1200, new Object[]{f1, 1500f});
        runLoad(1300, new Object[]{f1, 2000f});

        f1 = new FlowOne("two", 13);
        runLoad(500, new Object[]{f1, 10f});
        runLoad(900, new Object[]{f1, 100f});
        runLoad(1200, new Object[]{f1, 1000f});
    }

    private void runLoad(long testDurationMillis, Object[]... flowsThroughput) {
        LoadRunner runner = new LoadRunner();
        int numThreads = 1;
        for (Object[] pair : flowsThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            float throughput = (float) pair[1];
            runner.addFlow(flow, throughput);
            int requiredThreads = (int) ((throughput * (flow.getExpectedDurationMillis() + OVERHEAD_MILLIS)) / 1000) + 1;
            numThreads = Math.max(numThreads, requiredThreads);
        }


        runner.startLoad(numThreads);
        try {
            Thread.sleep(testDurationMillis);
        } catch (InterruptedException e) {
            // ignore
        }
        runner.stopLoad(1);
        float duration = runner.getLoadDuration();

        int finalNumThreads = numThreads;
        Arrays.stream(flowsThroughput).forEach(pair -> {
            String flowId = ((FlowMockup) pair[0]).getId();
            ValuePair<Float, Float> expectedAndActual= runner.getExpectedAndActualThroughput(flowId);
            Float expected = expectedAndActual.value1;
            Float actual = expectedAndActual.value2;
            System.out.println(flowId + " in " + finalNumThreads + " threads: expected=" + expected + "; actual=" + actual);
            assertEquals(expected * duration, actual * duration, 1);
        });
    }
}
