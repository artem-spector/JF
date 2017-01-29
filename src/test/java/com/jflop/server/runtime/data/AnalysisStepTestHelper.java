package com.jflop.server.runtime.data;

import com.jflop.load.GeneratedFlow;
import com.jflop.load.LoadRunner;
import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.metric.MetricMetadata;
import com.jflop.server.runtime.data.processed.*;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * TODO: Document!
 *
 * @author artem on 25/01/2017.
 */
public class AnalysisStepTestHelper {

    private List<GeneratedFlow> generatedFlows;
    private List<Float> expectedThroughputs;

    private Map<ThreadMetadata, List<ThreadOccurrenceData>> threads;
    private Map<FlowMetadata, List<FlowOccurrenceData>> flows;
    private final List<LoadData> loadData;
    private FlowSummary flowSummaryCalculated;
    private MetricMetadata metricMetadata;

    public AnalysisStepTestHelper(JvmMonitorAnalysis.StepState state, Object[][] flowsAndThroughput) {
        this.threads = state.threads;
        this.flows = state.flows;
        this.loadData = state.loadData;
        this.metricMetadata = state.metricMetadata;

        if (flowsAndThroughput != null) {
            generatedFlows = new ArrayList<>();
            expectedThroughputs = new ArrayList<>();
            for (Object[] pair : flowsAndThroughput) {
                generatedFlows.add((GeneratedFlow) pair[0]);
                expectedThroughputs.add((Float) pair[1]);
            }
        }
    }

    public FlowSummary getFlowSummary() {
        if (flowSummaryCalculated == null) {
            flowSummaryCalculated = new FlowSummary();
            flowSummaryCalculated.aggregateFlows(flows);
            flowSummaryCalculated.aggregateThreads(threads);
        }
        return flowSummaryCalculated;
    }

    public List<Set<String>> groupSameFlows() {
        Map<String, FlowMetadata> allFlows = getAllFlowMetadata();
        List<Set<String>> sameGroups = new ArrayList<>();

        for (String flowId : allFlows.keySet()) {
            boolean found = false;
            for (Set<String> sameGroup : sameGroups) {
                for (String sameId : sameGroup) {
                    if (flowsMaybeSame(flowId, sameId)) {
                        found = true;
                        sameGroup.add(flowId);
                        break;
                    }
                }
            }

            if (!found) {
                Set<String> group = new HashSet<>();
                group.add(flowId);
                sameGroups.add(group);
            }
        }

        return sameGroups;
    }

    public boolean flowsMaybeSame(String id1, String id2) {
        Map<String, FlowMetadata> allFlows = getAllFlowMetadata();
        return id1.equals(id2) || FlowMetadata.maybeSame(allFlows.get(id1), allFlows.get(id2));
    }

    public void checkThreadsCoverage() {
        FlowSummary flowSummary = getFlowSummary();

        for (ThreadMetadata threadMetadata : threads.keySet()) {
            boolean covered = false;
            StackTraceElement[] trace = threadMetadata.stackTrace;
            for (StackTraceElement element : trace) {
                if (flowSummary.isInstrumented(element)) {
                    covered = true;
                    break;
                }
            }
            List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
            boolean found = flowSummary.roots.stream().anyMatch(root -> flowSummary.findPath(root, trace, trace.length - 1) != null);
            assertEquals("Thread " + threadMetadata.getDocumentId() + " covered=" + covered + ", but found=" + found, covered, found);
        }
    }

    public void calculateDistanceAndOutline() {
        FlowSummary summary = getFlowSummary();

        Set<String> allFlows = new HashSet<>();
        summary.roots.forEach(root -> root.flows.forEach(flow -> allFlows.add(flow.flowId)));
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
        Map<String, ThreadMetadata> allThreads = new HashMap<>();
        threads.keySet().forEach(threadMetadata -> allThreads.put(threadMetadata.getDocumentId(), threadMetadata));
        FlowOutline outline = summary.buildOutline(flow1, allThreads);
        String flowStr = outline.format(true);
        System.out.println(flowStr);
    }

    public Map<String, Float> createMetrics() throws IOException {
        Map<String, Float> observation = new TreeMap<>();

        if (threads != null) {
            for (List<ThreadOccurrenceData> occurrenceList : threads.values()) {
                metricMetadata.aggregateThreads(occurrenceList, observation);
            }
        }

        if (flows != null) {
            for (List<FlowOccurrenceData> occurrenceList : flows.values()) {
                metricMetadata.aggregateFlows(occurrenceList, observation);
            }
        }

        if (loadData != null)
            metricMetadata.aggregateLoad(loadData, observation);

        System.out.println("metric line:");
        for (Map.Entry<String, Float> entry : observation.entrySet()) {
            System.out.println(entry.getKey() + "->" + entry.getValue());
        }

        return observation;
    }

    public Map<String, Set<String>> checkFlowStatistics(LoadRunner.LoadResult loadResult, float allowedMistake) {
        Map<String, Set<String>> res = new HashMap<>();
        Map<String, FlowMetadata> allFlows = getAllFlowMetadata();
        FlowSummary flowSummary = getFlowSummary();

        for (int i = 0; i < generatedFlows.size(); i++) {
            GeneratedFlow flow = generatedFlows.get(i);
            float expectedThroughput = expectedThroughputs.get(i);

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

            System.out.println("Flow " + flow);
            System.out.println("\tExpected       : throughput=" + expectedThroughput + "; duration=" + flow.getExpectedDurationMillis());

            Float actualThroughput = null;
            if (loadResult != null) {
                LoadRunner.FlowStats loadFlowStatistics = loadResult.flows.get(flow.getId());
                assertNotNull(loadFlowStatistics);
                actualThroughput = (float) loadFlowStatistics.executed / loadResult.durationMillis * 1000;
                System.out.println("\tLoad result    : throughput=" + actualThroughput + "; avgDuration=" + loadFlowStatistics.averageDuration);
            }

            System.out.println("\tFlow statistics: throughput=" + flowStatistics.throughputPerSec + "; avgDuration=" + flowStatistics.averageTime + "; minDuration=" + flowStatistics.minTime + "; maxDuration=" + flowStatistics.maxTime);

            if (loadResult != null)
                assertEquals(actualThroughput, flowStatistics.throughputPerSec, actualThroughput * allowedMistake);

            res.put(flow.getId(), found);

        }

        return res;
    }

    private Map<String, FlowMetadata> getAllFlowMetadata() {
        Map<String, FlowMetadata> allFlows = new HashMap<>();
        for (FlowMetadata flowMetadata : flows.keySet()) {
            allFlows.put(flowMetadata.getDocumentId(), flowMetadata);
        }
        return allFlows;
    }

}
