package com.jflop.server.runtime.data;

import com.jflop.TestUtil;
import com.jflop.load.GeneratedFlow;
import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.processed.FlowOutline;
import com.jflop.server.runtime.data.processed.FlowSummary;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.util.DebugPrintUtil;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 19/01/2017.
 */
public class SampleTest {

    @Test
    public void testRecorded() throws IOException {
        String folderPath = "samples/analysisSteps/1/";
        Object[][] flowsAndThroughput = GeneratedFlow.read(getClasspathFile(folderPath + "generatedFlows.json"));

        for (File stepFile : getFilesInFolder(folderPath, "step", ".json")) {
            AnalysisStepTestHelper helper = new AnalysisStepTestHelper(readStepStateFromFile(folderPath + stepFile.getName()), flowsAndThroughput);
            helper.checkFlowStatistics(null, false);
        }
    }

    @Test
    public void testSameFlows() throws IOException {
        assertEquals(2, countSame("samples/sameFlows/1/", "flow1.json", "flow2.json"));
        assertEquals(1, countSame("samples/sameFlows/2/", "flow1.json", "flow2.json"));
    }

    @Test
    public void testFindFlowInSummary() throws IOException {
        findFlowInSummary("samples/flowSummary/1/");
        findFlowInSummary("samples/flowSummary/2/");
        findFlowInSummary("samples/flowSummary/3/");
        findFlowInSummary("samples/flowSummary/4/");
        findFlowInSummary("samples/flowSummary/5/");
    }

    @Test
    public void testMapThreadsToFlows() throws IOException {
        mapThreadsToFlows("samples/threadsAndFlows/1/");
        mapThreadsToFlows("samples/threadsAndFlows/2/");
    }

    @Test
    public void testGroupFlowSummary() throws IOException {
        groupFlowSummary("samples/threadsAndFlows/2/");
    }

    @Test
    public void testCreateMetrics() throws IOException {
        createMetrics("samples/analysisSteps/1/");
    }

    private void createMetrics(String folderPath) throws IOException {

        for (File stepFile : getFilesInFolder(folderPath, "step", ".json")) {
            JvmMonitorAnalysis.StepState stepState = readStepStateFromFile(folderPath + stepFile.getName());
            MetricMetadata metricMetadata = new MetricMetadata();
            Map<String, Float> observation = new TreeMap<>();

            for (List<FlowOccurrenceData> occurrenceList : stepState.flows.values()) {
                for (FlowOccurrenceData occurrence : occurrenceList) {
                    collectFlowMetrics(metricMetadata, observation, occurrence.snapshotDurationSec, occurrence.rootFlow);
                }
            }

            System.out.println("metric line:");
            for (Map.Entry<String, Float> entry : observation.entrySet()) {
                System.out.println(entry.getKey() + "->" + entry.getValue());
            }
        }
    }

    private void collectFlowMetrics(MetricMetadata metricBuilder, Map<String, Float> observation, float snapshotDurationSec, FlowOccurrenceData.FlowElement element) {
        metricBuilder.aggregate(snapshotDurationSec, element, observation);
        if (element.subflows != null) {
            element.subflows.forEach(subflow -> collectFlowMetrics(metricBuilder, observation, snapshotDurationSec, subflow));
        }
    }

    private void groupFlowSummary(String folderPath) throws IOException {
        JvmMonitorAnalysis.StepState stepState = buildFlowSummary(folderPath, true);
        FlowSummary summary = stepState.flowSummary;

        Set<String> allFlows = new HashSet<>();
        summary.roots.forEach(root -> root.flows.forEach(flow ->allFlows.add(flow.flowId)));
        String[] array = allFlows.toArray(new String[allFlows.size()]);

        for (int i = 0; i < array.length; i++) {
            String flow1 = array[i];
            for (int j = i + 1; j < array.length; j++) {
                String flow2 = array[j];
                float distance = summary.calculateDistance(flow1, flow2);
                System.out.println(flow1 + " - " + flow2 + " = " + distance);
            }
        }

        String flow1 = array[0];
        Map<String, ThreadMetadata> threads = new HashMap<>();
        stepState.threads.keySet().forEach(threadMetadata -> threads.put(threadMetadata.getDocumentId(), threadMetadata));
        FlowOutline outline = summary.buildOutline(flow1, threads);
        String flowStr = outline.format(true);
        System.out.println(flowStr);
    }

    private void mapThreadsToFlows(String folderPath) throws IOException {
        JvmMonitorAnalysis.StepState stepState = buildFlowSummary(folderPath, true);

        for (ThreadMetadata threadMetadata : stepState.threads.keySet()) {
            boolean covered = false;
            StackTraceElement[] trace = threadMetadata.stackTrace;
            for (StackTraceElement element : trace) {
                if (stepState.flowSummary.isInstrumented(element)) {
                    covered = true;
                    break;
                }
            }
            List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
            boolean found = stepState.flowSummary.roots.stream().anyMatch(root -> stepState.flowSummary.findPath(root, trace, trace.length - 1, path));
            assertEquals("Thread " + threadMetadata.getDocumentId() + " covered=" + covered + ", but found=" + found, covered, found);
        }
    }

    private JvmMonitorAnalysis.StepState buildFlowSummary(String folderPath, boolean printSummary) throws IOException {
        JvmMonitorAnalysis.StepState res = new JvmMonitorAnalysis.StepState();
        res.threads = new HashMap<>();
        res.flows = new HashMap<>();

        for (File file : getFilesInFolder(folderPath, "threadMetadata", ".json")) {
            res.threads.put(readFromFile(folderPath + file.getName(), ThreadMetadata.class), new ArrayList<>());
        }
        for (File file : getFilesInFolder(folderPath, "threadOccurrence", ".json")) {
            ThreadOccurrenceData data = readFromFile(folderPath + file.getName(), ThreadOccurrenceData.class);
            for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> entry : res.threads.entrySet()) {
                if (entry.getKey().getDocumentId().equals(data.getMetadataId())) {
                    entry.getValue().add(data);
                    break;
                }
            }
        }

        for (File file : getFilesInFolder(folderPath, "flowMetadata", ".json")) {
            res.flows.put(readFromFile(folderPath + file.getName(), FlowMetadata.class), new ArrayList<>());
        }
        for (File file : getFilesInFolder(folderPath, "flowOccurrence", ".json")) {
            FlowOccurrenceData data = readFromFile(folderPath + file.getName(), FlowOccurrenceData.class);
            for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> entry : res.flows.entrySet()) {
                if (entry.getKey().getDocumentId().equals(data.getMetadataId())) {
                    entry.getValue().add(data);
                    break;
                }
            }
        }

        res.flowSummary = new FlowSummary();
        res.flowSummary.aggregateFlows(res.flows);
        res.flowSummary.aggregateThreads(res.threads);
        System.out.println(DebugPrintUtil.printFlowSummary(res.flowSummary, printSummary));
        return res;
    }

    private int countSame(String folderPath, String... flowFiles) throws IOException {
        Map<String, FlowMetadata> allFlows = new HashMap<>();
        for (String flowFile : flowFiles) {
            FlowMetadata metadata = readFromFile(folderPath + flowFile, FlowMetadata.class);
            allFlows.put(metadata.getDocumentId(), metadata);
        }

        Set<String> same = new HashSet<>();
        for (String flowId : allFlows.keySet()) {
            boolean isSame = true;
            for (String sameFlowId : same) {
                if (!FlowMetadata.maybeSame(allFlows.get(flowId), allFlows.get(sameFlowId))) {
                    System.out.println(flowId + " cannot not be same as " + sameFlowId);
                    isSame = false;
                    break;
                }
            }
            if (isSame) same.add(flowId);
        }

        return same.size();
    }

    private void findFlowInSummary(String folderPath) throws IOException {
        FlowSummary summary = readFromFile(folderPath + "summary1.json", FlowSummary.class);

        Map<String, FlowMetadata> flows = new HashMap<>();
        for (File file : getFilesInFolder(folderPath, "flow", ".json")) {
            FlowMetadata flowMetadata = readFromFile(folderPath + file.getName(), FlowMetadata.class);
            flows.put(flowMetadata.getDocumentId(), flowMetadata);
        }

        String generatedFlowFile = new ClassPathResource(folderPath + "generatedFlow1.json").getURL().getPath();
        GeneratedFlow generatedFlow = GeneratedFlow.fromString(TestUtil.readStringFromFile(generatedFlowFile));

        Set<String> found = generatedFlow.findFlowIds(summary, flows);
        assertNotNull(found);
        assertFalse(found.isEmpty());

        if (found.size() > 1) {
            Set<String> same = new HashSet<>();
            for (String flowId : found) {
                if (!same.isEmpty())
                    for (String sameFlowId : same) {
                        boolean maybeSame = FlowMetadata.maybeSame(flows.get(flowId), flows.get(sameFlowId));
                        assertTrue("Flows " + flowId + " and " + sameFlowId + " cannot represent same flow", maybeSame);
                    }
                same.add(flowId);
            }
        }
        System.out.println("Flow found: " + found);
    }

    private <T> T readFromFile(String path, Class<T> valueType) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return TestUtil.readValueFromFile(rsc.getURL().getPath(), valueType);
    }

    private File[] getFilesInFolder(String folderPath, String prefix, String suffix) throws IOException {
        ClassPathResource rsc = new ClassPathResource(folderPath);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        File folder = new File(rsc.getURL().getPath());
        assertTrue("Not a folder: " + folder.getAbsolutePath(), folder.isDirectory());

        return folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(suffix));
    }

    private JvmMonitorAnalysis.StepState readStepStateFromFile(String path) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return JvmMonitorAnalysis.StepState.readFromFile(rsc.getFile());
    }

    private File getClasspathFile(String path) throws IOException {
        ClassPathResource rsc = new ClassPathResource(path);
        assertTrue("File does not exist: " + rsc.getURL(), rsc.exists());
        return rsc.getFile();
    }
}
