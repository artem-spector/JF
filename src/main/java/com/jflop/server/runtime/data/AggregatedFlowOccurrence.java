package com.jflop.server.runtime.data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Includes the combined statistics of the flow and related threads, aggregated within the analysis interval.
 *
 * @author artem on 04/01/2017.
 */
public class AggregatedFlowOccurrence extends OccurrenceData {

    public AggregatedFlowElement rootFlow;

    public static Map<FlowMetadata, AggregatedFlowOccurrence> aggregate(
            Map<FlowMetadata, List<FlowOccurrenceData>> flows, Map<ThreadMetadata, List<ThreadOccurrenceData>> threads,
            AgentDataFactory dataFactory, Set<StackTraceElement> instrumentedElements, long intervalMillis) {

        // 1. aggregate flow occurrences
        Map<FlowMetadata, AggregatedFlowOccurrence> aggregatedFlows = new HashMap<>();
        for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> entry : flows.entrySet()) {
            AggregatedFlowOccurrence occurrence = dataFactory.createInstance(AggregatedFlowOccurrence.class);
            occurrence.processFlowOccurrences(entry.getValue(), intervalMillis);
            aggregatedFlows.put(entry.getKey(), occurrence);
        }

        // 2. aggregate thread occurrences
        Map<ThreadMetadata, ThreadStatistics> aggregatedThreads = new HashMap<>();
        for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> entry : threads.entrySet()) {
            aggregatedThreads.put(entry.getKey(), new ThreadStatistics(entry.getValue()));
        }

        // 3. map threads to flows
        Map<ThreadMetadata, Float> threadToFlowsThroughput = new HashMap<>();
        Map<FlowMetadata, Map<ThreadMetadata, List<String>>> flowToThreadPaths = new HashMap<>();
        for (FlowMetadata flowMetadata : flows.keySet()) {
            for (ThreadMetadata threadMetadata : threads.keySet()) {
                List<String> path = flowMetadata.getThreadPath(threadMetadata.stackTrace, instrumentedElements);
                if (path != null) {
                    threadToFlowsThroughput.compute(threadMetadata, (key, value) -> (value == null ? 0 : value) + aggregatedFlows.get(flowMetadata).rootFlow.throughputPerSec);
                    flowToThreadPaths.computeIfAbsent(flowMetadata, key -> new HashMap<>()).computeIfAbsent(threadMetadata, key -> path);
                }
            }
        }

        // 4. add thread statistics to the aggregated flows
        for (Map.Entry<FlowMetadata, Map<ThreadMetadata, List<String>>> entry : flowToThreadPaths.entrySet()) {
            FlowMetadata flowMetadata = entry.getKey();
            AggregatedFlowOccurrence occurrence = aggregatedFlows.get(flowMetadata);

            // loop by fitting threads
            for (Map.Entry<ThreadMetadata, List<String>> threadToPath : entry.getValue().entrySet()) {
                // get partial thread statistics according to the flow throughput
                ThreadMetadata threadMetadata = threadToPath.getKey();
                Float threadThroughput = threadToFlowsThroughput.get(threadMetadata);
                float flowThroughput = occurrence.rootFlow.throughputPerSec;
                ThreadStatistics statistics = aggregatedThreads.get(threadMetadata).partial(flowThroughput /threadThroughput);

                // set the statistics through the path
                List<String> path = threadToPath.getValue();
                occurrence.rootFlow.setThreadStatistics(statistics, path, 0);
            }
        }

        return aggregatedFlows;
    }

    private void processFlowOccurrences(List<FlowOccurrenceData> flowOccurrences, long intervalMillis) {
        rootFlow = new AggregatedFlowElement(flowOccurrences.stream().map(flowOccurrence -> flowOccurrence.rootFlow).collect(Collectors.toList()), intervalMillis);
    }

    @Override
    public String getMetadataId() {
        return rootFlow.flowId;
    }

    public static class AggregatedFlowElement {

        public String flowId;
        public float throughputPerSec;
        public long minTime;
        public long maxTime;
        public long averageTime;

        public List<AggregatedFlowElement> subflows;

        public ThreadStatistics threadStatistics;

        public AggregatedFlowElement(List<FlowOccurrenceData.FlowElement> raw, long intervalMillis) {
            minTime = Long.MAX_VALUE;
            FlowOccurrenceData.FlowElement first = raw.get(0);
            this.flowId = first.flowId;

            int count = 0;
            long cumulativeTime = 0;
            for (FlowOccurrenceData.FlowElement element : raw) {
                assert this.flowId.equals(element.flowId);
                count += element.count;
                cumulativeTime += element.cumulativeTime;
                this.minTime = Math.min(this.minTime, element.minTime);
                this.maxTime = Math.max(this.maxTime, element.maxTime);
            }
            this.averageTime = cumulativeTime / count;
            this.throughputPerSec = (float) count / ((float) intervalMillis / 1000);

            if (first.subflows != null && !first.subflows.isEmpty()) {
                this.subflows = new ArrayList<>();
                for (FlowOccurrenceData.FlowElement subflow : first.subflows) {
                    this.subflows.add(new AggregatedFlowElement(getSubflowOccurrences(raw, subflow.flowId), intervalMillis));
                }
            }
        }

        public void setThreadStatistics(ThreadStatistics statistics, List<String> path, int pathPos) {
            assert path.get(pathPos).equals(flowId);
            if (this.threadStatistics == null || this.threadStatistics.threadCount < statistics.threadCount)
                this.threadStatistics = statistics;

            if (pathPos == path.size() - 1) return;
            pathPos++;
            String next = path.get(pathPos);
            for (AggregatedFlowElement subflow : subflows) {
                if (subflow.flowId.equals(next)) {
                    subflow.setThreadStatistics(statistics, path, pathPos);
                }
            }
        }

        private List<FlowOccurrenceData.FlowElement> getSubflowOccurrences(List<FlowOccurrenceData.FlowElement> parentOccurrences, String subflowId) {
            List<FlowOccurrenceData.FlowElement> res = new ArrayList<>();
            for (FlowOccurrenceData.FlowElement parentOccurrence : parentOccurrences) {
                for (FlowOccurrenceData.FlowElement subflow : parentOccurrence.subflows) {
                    if (subflow.flowId.equals(subflowId)) res.add(subflow);
                }
            }
            return res;
        }
    }

    public static class ThreadStatistics {

        public String threadId;
        public Thread.State threadState;
        public float threadCount;

        public ThreadStatistics(String threadId, Thread.State threadState, float threadCount) {
            this.threadId = threadId;
            this.threadState = threadState;
            this.threadCount = threadCount;
        }

        public ThreadStatistics(List<ThreadOccurrenceData> raw) {
            ThreadOccurrenceData first = raw.get(0);
            this.threadId = first.dumpId;
            this.threadState = first.threadState;
            for (ThreadOccurrenceData data : raw) {
                threadCount += data.count;
            }
            threadCount = threadCount / raw.size();
        }

        public ThreadStatistics partial(float part) {
            ThreadStatistics res = new ThreadStatistics(threadId, threadState, threadCount * part);
            return res;
        }
    }
}
