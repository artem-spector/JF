package com.jflop.load;

import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.background.LockIndex;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.runtime.MetadataIndex;
import com.jflop.server.runtime.ProcessedDataIndex;
import com.jflop.server.runtime.data.AnalysisStepTestHelper;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.runtime.data.processed.MethodFlowStatistics;
import com.jflop.server.util.DebugPrintUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

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
    private LockIndex lockIndex;

    @Autowired
    private JvmMonitorAnalysis analysisTask;

    private boolean strictThroughputCheck;
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
        generateFlows(1, 20, 20, 100, 100);

        strictThroughputCheck = false;

        startLoad();
        startMonitoring();
        awaitNextSummary(30, null); // skip the first summary

        int numIterations = 3;
        Map<String, Set<String>> found = null;
        for (int i = 0; i < numIterations; i++) {
            found = findFlowsInNextSummary(10, found);
        }

        stopMonitoring();
        stopLoad();
    }

    @Test
    public void testSingleFlow_new() throws Exception {
        Object[][] generated = generateFlows(1, 20, 20, 100, 100);
        runFlows(generated, 3, "target/testSingleFlow-temp");
    }

    private void runFlows(Object[][] generatedFlows, int numIterations, String folderPath) throws Exception {
        startLoad();
        startMonitoring();
        awaitNextSummary(30, null); // skip the first summary

        File folder = prepareFolder(folderPath);

        Map<String, Set<String>> previous = null;
        for (int i = 0; i < numIterations; i++) {
            File file = new File(folder, "step" + (i + 1) + ".json");
            analysisTask.saveStepToFile(file);
            awaitNextSummary(10, new Date());
            LoadRunner.LoadResult loadResult = getLoadResult();

            AnalysisStepTestHelper helper = new AnalysisStepTestHelper(JvmMonitorAnalysis.StepState.readFromFile(file), generatedFlows);
            Map<String, Set<String>> found = helper.checkFlowStatistics(loadResult, false);
            if (previous != null) {
                assertEquals(previous.keySet(), found.keySet());
                for (Object[] pair : generatedFlows) {
                    GeneratedFlow generatedFlow = (GeneratedFlow) pair[0];
                    for (String prevFlowId : previous.get(generatedFlow.getId())) {
                        for (String foundFlowId : found.get(generatedFlow.getId())) {
                            String message = "Flows " + prevFlowId + " and " + foundFlowId + " cannot represent the same generated flow:\n" + generatedFlow;
                            assertTrue(message, prevFlowId.equals(foundFlowId) || flowsMaybeSame(prevFlowId, foundFlowId));
                        }
                    }
                }
            }
            previous = found;
        }

        stopMonitoring();
        stopLoad();
    }

    @Test
    public void testMultipleFlows() throws Exception {
        int numFlows = 6;
        generateFlows(numFlows, 10, 100, 50, 200);
        System.out.println("Generated flows:");
        for (Object[] pair : flowsAndThroughput) System.out.println(pair[0]);

        startLoad();
        startMonitoring();
        awaitNextSummary(30, null); // skip the first summary

        File folder = prepareFolder("target/testMultipleFlows-temp");

        int numIterations = 3;
        Map<String, Set<String>> found = null;
        for (int i = 0; i < numIterations; i++) {
            analysisTask.saveStepToFile(new File(folder, "step" + (i + 1) + ".json"));
            found = findFlowsInNextSummary(10, found);
        }

        stopMonitoring();
        stopLoad();
    }

    private File prepareFolder(String path) {
        File folder = new File(path);
        if (folder.exists()) {
            for (File file : folder.listFiles()) file.delete();
        } else {
            folder.mkdirs();
        }
        return folder;
    }

    private Map<String, Set<String>> findFlowsInNextSummary(int timeoutSec, Map<String, Set<String>> previous) {
        awaitNextSummary(timeoutSec, new Date());
        assertNotNull(flowSummary);
        logger.fine(DebugPrintUtil.printFlowSummary(flowSummary, true));
        LoadRunner.LoadResult loadResult = getLoadResult();
        Map<String, Set<String>> found = new HashMap<>();
        for (Object[] pair : flowsAndThroughput) {
            GeneratedFlow flow = (GeneratedFlow) pair[0];
            float expectedThroughput = (float) pair[1];
            found.put(flow.getId(), checkFlowStatistics(flow, expectedThroughput, loadResult));
        }

        if (previous != null) {
            assertEquals(previous.keySet(), found.keySet());
            for (String generatedFlowId : previous.keySet()) {
                for (String prevFlowId : previous.get(generatedFlowId)) {
                    for (String foundFlowId : found.get(generatedFlowId)) {
                        String message = "Flows " + prevFlowId + " and " + foundFlowId + " cannot represent the same flow:\n" + flowsAndThroughput[0][0];
                        assertTrue(message, prevFlowId.equals(foundFlowId) || flowsMaybeSame(prevFlowId, foundFlowId));
                    }
                }
            }
        }

        return found;
    }

    private void awaitNextSummary(int timeoutSec, Date begin) {
        flowSummary = null;
        logger.fine("Begin waiting for flow summary from " + begin);
        long border = System.currentTimeMillis() + timeoutSec * 1000;
        String oldMsg = "";
        while (System.currentTimeMillis() < border) {
            try {
                flowSummary = processedDataIndex.getLastSummary();
                if (logger.isLoggable(Level.FINE)) {
                    String msg = "Read flow summary " + (flowSummary == null ? "null" : "of time " + flowSummary.time);
                    if (!oldMsg.equals(msg))
                        logger.fine(msg);
                    oldMsg = msg;
                }
                if (flowSummary != null && (begin == null || flowSummary.time.after(begin))) {
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
        Map<String, FlowMetadata> allFlows = new HashMap<>();
        flowSummary.roots.forEach(root -> root.flows.forEach(f -> allFlows.put(f.flowId, getFlowMetadata(f.flowId))));

        Set<String> found = flow.findFlowIds(flowSummary, allFlows);
        assertFalse("Flow not found in the summary:\n" + flow.toString(), found.isEmpty());

        Set<String> maybeSame = new HashSet<>();
        for (String flowId : found) {
            for (String other : maybeSame) {
                if (!flowsMaybeSame(flowId, other))
                    fail("Flows " + flowId + " and " + other + " cannot represent the same flow, but found fit the generated flow:\n" + flow);
            }
            maybeSame.add(flowId);
        }

        assertFalse("Flow not found in summary:\n" + flow.toString(), found.isEmpty());

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
        assertEquals(actualThroughput, flowStatistics.throughputPerSec, strictThroughputCheck ? actualThroughput / 10 : actualThroughput / 3);
        return found;
    }

    private boolean flowsMaybeSame(String flowId1, String flowId2) {
        if (flowId1.equals(flowId2)) return true;
        FlowMetadata flow1 = getFlowMetadata(flowId1);
        FlowMetadata flow2 = getFlowMetadata(flowId2);
        return FlowMetadata.maybeSame(flow1, flow2);
    }

    private FlowMetadata getFlowMetadata(String id) {
        PersistentData<FlowMetadata> document = metadataIndex.getDocument(new PersistentData<>(id, 0), FlowMetadata.class);
        assertNotNull(document);
        return document.source;
    }

}
