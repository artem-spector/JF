package com.jflop.load;

import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.runtime.data.processed.MethodFlowStatistics;
import org.jflop.config.JflopConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 15/01/2017.
 */
public class AnalysisTest extends LoadTestBase {

    @Autowired
    private ProcessedDataIndex processedDataIndex;

    @Autowired
    private InstrumentationConfigurationFeature instrumentationConfigurationFeature;
    private JflopConfiguration instrumentationConfig;

    public AnalysisTest() {
        super("AnalysisTest");
    }

    @Before
    public void startClient() throws Exception {
        startClient("analysisTestAgent");
    }

    @Test
    public void testSingleFlow() throws Exception {
        // TODO: automatically save/grab problematic flows
        generateFlows(1, 10, 10, 100, 100);

/*
        GeneratedFlow problematic = GeneratedFlow.fromString("{\"name\":\"m1\",\"duration\":33,\"nested\":[{\"name\":\"m6\",\"duration\":31,\"nested\":[{\"name\":\"m3\",\"duration\":1,\"nested\":[{\"name\":\"m4\",\"duration\":17,\"nested\":[{\"name\":\"m7\",\"duration\":15},{\"name\":\"m5\",\"duration\":5}]},{\"name\":\"m2\",\"duration\":28,\"nested\":[{\"name\":\"m8\",\"duration\":2}]}]}]}]}");
        flowsAndThroughput = new Object[][]{new Object[]{problematic, 10f}};
*/

        startLoad();
        startMonitoring();
        FlowSummary summary = awaitNextSummary(30);
        assertNotNull(summary);
        LoadRunner.LoadResult loadResult = stopLoad();
        stopMonitoring();

        GeneratedFlow flow = (GeneratedFlow) flowsAndThroughput[0][0];
        float expectedThroughput = (float) flowsAndThroughput[0][1];
        checkFlowStatistics(flow, expectedThroughput, loadResult, summary);
    }

    private FlowSummary awaitNextSummary(int timeoutSec) {
        Date begin = new Date();
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        while (System.currentTimeMillis() < border) {
            try {
                FlowSummary summary = processedDataIndex.getLastSummary();
                if (summary != null && summary.time.after(begin)) {
                    instrumentationConfig = instrumentationConfigurationFeature.getConfiguration(currentJvm);
                    return summary;
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        fail("Summary not produced in " + timeoutSec + " sec");
        return null;
    }

    private void checkFlowStatistics(GeneratedFlow flow, float expectedThroughput, LoadRunner.LoadResult loadResult, FlowSummary summary) {
        Set<String> found = flow.findFlowIds(summary, instrumentationConfig);
        assertFalse("Flow not found in the summary:\n" + flow.toString(), found.isEmpty());
        assertEquals("Found " + found.size() + " flows in the summary for:\n" + flow.toString(), 1, found.size());

        String flowId = found.iterator().next();
        MethodFlowStatistics flowStatistics = null;
        for (MethodCall root : summary.roots) {
            for (MethodFlow methodFlow : root.flows) {
                if (methodFlow.flowId.equals(flowId)) {
                    flowStatistics = methodFlow.statistics;
                    break;
                }
            }
        }
        assertNotNull(flowStatistics);

        LoadRunner.FlowStats loadFlowStatistics = loadResult.flows.get(flow.getId());
        assertNotNull(loadFlowStatistics);
        float actualThroughput = (float) loadFlowStatistics.executed / loadResult.durationMillis * 1000;

        System.out.println("Expected       : throughput=" + expectedThroughput + "; duration=" + flow.getExpectedDurationMillis());
        System.out.println("Load result    : throughput=" + actualThroughput + "; avgDuration=" + loadFlowStatistics.averageDuration);
        System.out.println("Flow statistics: throughput=" + flowStatistics.throughputPerSec + "; avgDuration=" + flowStatistics.averageTime + "; minDuration=" + flowStatistics.minTime + "; maxDuration=" + flowStatistics.maxTime);
        assertEquals(actualThroughput, flowStatistics.throughputPerSec, actualThroughput / 10);

    }

}
