package com.jflop.load;

import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.runtime.data.processed.MethodFlowStatistics;
import org.jflop.config.JflopConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

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
    private MetadataIndex metadataIndex;

    @Autowired
    private InstrumentationConfigurationFeature instrumentationConfigurationFeature;

    private JflopConfiguration instrumentationConfig;
    private FlowSummary flowSummary;

    public AnalysisTest() {
        super("AnalysisTest", true);
    }

    @Before
    public void startClient() throws Exception {
        startClient("analysisTestAgent");
    }

    @Test
    public void testSingleFlow() throws Exception {
        // TODO: automatically save/grab problematic flows
//        String flowStr1 = "{\"name\":\"m1\",\"duration\":33,\"nested\":[{\"name\":\"m6\",\"duration\":31,\"nested\":[{\"name\":\"m3\",\"duration\":1,\"nested\":[{\"name\":\"m4\",\"duration\":17,\"nested\":[{\"name\":\"m7\",\"duration\":15},{\"name\":\"m5\",\"duration\":5}]},{\"name\":\"m2\",\"duration\":28,\"nested\":[{\"name\":\"m8\",\"duration\":2}]}]}]}]}";
        String flowStr1 = "{\"name\":\"m2\",\"duration\":0,\"nested\":[{\"name\":\"m7\",\"duration\":7,\"nested\":[{\"name\":\"m5\",\"duration\":23},{\"name\":\"m4\",\"duration\":23},{\"name\":\"m8\",\"duration\":16,\"nested\":[{\"name\":\"m6\",\"duration\":30},{\"name\":\"m3\",\"duration\":27,\"nested\":[{\"name\":\"m1\",\"duration\":12}]}]}]}]}";
        flowsAndThroughput = new Object[][]{new Object[]{GeneratedFlow.fromString(flowStr1), 10f}};
//        generateFlows(1, 20, 20, 100, 100);

        startLoad();
        startMonitoring();
        awaitNextSummary(30); // skip the first summary
        Map<String, Set<String>> foundFlowIds1 = findFlowsInNextSummary(10);
        Map<String, Set<String>> foundFlowIds2 = findFlowsInNextSummary(10);
        assertEquals(foundFlowIds1, foundFlowIds2);
        stopMonitoring();
        stopLoad();
    }

    @Test
    public void testMultipleFlows() throws Exception {
        int numFlows = 4;
        generateFlows(numFlows, 20, 20, 100, 100);
        System.out.println("Generated flows:");
        for (Object[] pair : flowsAndThroughput) System.out.println(pair[0]);

        startLoad();
        startMonitoring();
        Map<String, Set<String>> flowIds = findFlowsInNextSummary(30);
        assertEquals(numFlows, flowIds.size());
        stopMonitoring();
        stopLoad();
    }

    private Map<String, Set<String>> findFlowsInNextSummary(int timeoutSec) {
        awaitNextSummary(timeoutSec);
        assertNotNull(flowSummary);
        LoadRunner.LoadResult loadResult = getLoadResult();
        Map<String, Set<String>> foundFlowIds = new HashMap<>();
        for (Object[] pair : flowsAndThroughput) {
            GeneratedFlow flow = (GeneratedFlow) pair[0];
            float expectedThroughput = (float) pair[1];
            foundFlowIds.put(flow.getId(), checkFlowStatistics(flow, expectedThroughput, loadResult));
        }
        return foundFlowIds;
    }

    private void awaitNextSummary(int timeoutSec) {
        flowSummary = null;
        instrumentationConfig = null;
        Date begin = new Date();
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        while (System.currentTimeMillis() < border) {
            try {
                flowSummary = processedDataIndex.getLastSummary();
                if (flowSummary != null && flowSummary.time.after(begin)) {
                    instrumentationConfig = instrumentationConfigurationFeature.getConfiguration(currentJvm);
                    assertNotNull(instrumentationConfig);
                    return;
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }

        fail("Summary not produced in " + timeoutSec + " sec");
    }

    private Set<String> checkFlowStatistics(GeneratedFlow flow, float expectedThroughput, LoadRunner.LoadResult loadResult) {
        Set<String> found = flow.findFlowIds(flowSummary, instrumentationConfig);
        assertFalse("Flow not found in the summary:\n" + flow.toString(), found.isEmpty());

        Set<FlowMetadata> maybeSame = new HashSet<>();
        for (String flowId : found) {
            PersistentData<FlowMetadata> document = metadataIndex.getDocument(new PersistentData<>(flowId, 0), FlowMetadata.class);
            assertNotNull(document);
            FlowMetadata flowMetadata = document.source;
            for (FlowMetadata other : maybeSame) {
                if (!flowMetadata.representsSameFlowAs(other))
                    fail("Flows " + flowMetadata.getDocumentId() + " and " + other.getDocumentId() + " cannot represent the same flow, but found fit the generated flow:\n" + flow);
            }
            maybeSame.add(flowMetadata);
        }

        assertEquals("Found " + found.size() + " flows in the summary for:\n" + flow.toString(), 1, found.size());

        MethodFlowStatistics flowStatistics = new MethodFlowStatistics();
        for (String flowId : found) {
            for (MethodCall root : flowSummary.roots) {
                for (MethodFlow methodFlow : root.flows) {
                    if (methodFlow.flowId.equals(flowId)) {
                        flowStatistics.merge(methodFlow.statistics);
                        break;
                    }
                }
            }
            assertNotNull(flowStatistics);
        }

        LoadRunner.FlowStats loadFlowStatistics = loadResult.flows.get(flow.getId());
        assertNotNull(loadFlowStatistics);
        float actualThroughput = (float) loadFlowStatistics.executed / loadResult.durationMillis * 1000;

        System.out.println("Flow " + flow);
        System.out.println("\tExpected       : throughput=" + expectedThroughput + "; duration=" + flow.getExpectedDurationMillis());
        System.out.println("\tLoad result    : throughput=" + actualThroughput + "; avgDuration=" + loadFlowStatistics.averageDuration);
        System.out.println("\tFlow statistics: throughput=" + flowStatistics.throughputPerSec + "; avgDuration=" + flowStatistics.averageTime + "; minDuration=" + flowStatistics.minTime + "; maxDuration=" + flowStatistics.maxTime);
        assertEquals(actualThroughput, flowStatistics.throughputPerSec, actualThroughput / 10);
        return found;
    }

}
