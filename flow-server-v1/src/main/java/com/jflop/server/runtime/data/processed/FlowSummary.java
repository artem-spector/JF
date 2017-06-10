package com.jflop.server.runtime.data.processed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.*;
import org.jflop.config.MethodConfiguration;
import org.jflop.config.NameUtils;
import sun.invoke.util.Wrapper;

import java.io.IOException;
import java.util.*;

/**
 * Contains processed data of a flow with its sub-flows and related stack traces within a time interval.
 * The {@link #time} field contains the beginning of the interval
 *
 * @author artem
 *         Date: 1/7/17
 */
public class FlowSummary extends AgentData {

    @JsonIgnore
    public List<MethodCall> roots;

    @JsonIgnore
    private Set<MethodConfiguration> allMethods;

    @JsonProperty
    public String getRootsStr() throws JsonProcessingException {
        return MAPPER.writeValueAsString(roots);
    }

    @JsonProperty
    public void setRootsStr(String str) throws IOException {
        roots = MAPPER.readValue(str, List.class);
    }

    public void aggregateFlows(Map<FlowMetadata, List<FlowOccurrenceData>> flows) {
        roots = new ArrayList<>();
        for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> entry : flows.entrySet()) {
            FlowMetadata flowMetadata = entry.getKey();
            List<FlowOccurrenceData> occurrences = entry.getValue();
            MethodCall call = MethodCall.getOrCreateCall(roots, flowMetadata.rootFlow);
            call.addFlow(flowMetadata, occurrences);
        }
    }

    public void aggregateThreads(Map<ThreadMetadata, List<ThreadOccurrenceData>> threads) {
        for (MethodCall root : roots) {
            for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> entry : threads.entrySet()) {
                ThreadMetadata threadMetadata = entry.getKey();
                StackTraceElement[] stackTrace = threadMetadata.stackTrace;
                if (stackTrace != null && stackTrace.length > 0) {
                    List<ThreadOccurrenceData> threadOccurrences = entry.getValue();
                    List<ValuePair<MethodCall, Integer>> path = findPath(root, stackTrace, stackTrace.length - 1);
                    if (path != null) {
                        root.addThread(path, threadOccurrences);
                    }
                }
            }
        }
    }

    public List<ValuePair<MethodCall, Integer>> findPath(MethodCall methodCall, StackTraceElement[] stacktrace, int tracePos) {
        // it's ok to skip instrumented elements, if they are in the beginning of the stack
        // this is because the outmost methods might not return yet, and the registered flow may be partial.
        boolean skipInstrumented = tracePos == stacktrace.length - 1;

        // skip not fitting trace elements if they are not instrumented, or if we are in the beginning of the stack trace
        boolean fit = false;
        while (tracePos >= 0) {
            fit = methodCall.fits(stacktrace[tracePos]);
            if (!fit && (skipInstrumented || !isInstrumented(stacktrace[tracePos])))
                tracePos--;
            else
                break;
        }

        // if no fitting element found in the stacktrace, it's not fit
        if (!fit)
            return null;

        // if the flow element fits the stacktrace element, add the method call and the line number to the path
        List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
        path.add(new ValuePair<>(methodCall, stacktrace[tracePos].getLineNumber()));

        // if we've reached the trace end, we're done
        if (tracePos == 0) return path;

        // if we've reached the flow end, we're done
        if (methodCall.nestedCalls == null || methodCall.nestedCalls.isEmpty()) {
            return path;
        }

        // go through the subflows and pick the longest sub-path
        List<ValuePair<MethodCall, Integer>> longestPath = null;
        for (MethodCall nested : methodCall.nestedCalls) {
            List<ValuePair<MethodCall, Integer>> nestedPath = findPath(nested, stacktrace, tracePos - 1);
            if (longestPath == null || (nestedPath != null && nestedPath.size() > longestPath.size()))
                longestPath = nestedPath;
        }
        if (longestPath != null)
            path.addAll(longestPath);

        return path;
    }

    public boolean isInstrumented(StackTraceElement element) {
        if (allMethods == null) {
            allMethods = new HashSet<>();
            roots.forEach(this::getInstrumentedMethods);
        }

        MethodConfiguration mtd = new MethodConfiguration(NameUtils.getInternalClassName(element.getClassName()), element.getMethodName(), "UNKNOWN");
        return allMethods.contains(mtd);
    }

    /**
     * Distance between the two flows is calculated as a number of the summary nodes where one flow presents and the other doesn't,
     * divided by the length of the longest flow.
     *
     * @param flow1 one flow, must present in the summary
     * @param flow2 another flow, must present in the summary
     * @return the distance, 0 means the flows are identical, 1 means they have no common nodes
     */
    public float calculateDistance(String flow1, String flow2) {
        boolean flow1Found = false;
        boolean flow2Found = false;
        for (MethodCall root : roots) {
            boolean flow1InThisRoot = root.flows.stream().anyMatch(flow -> flow.flowId.equals(flow1));
            boolean flow2InThisRoot = root.flows.stream().anyMatch(flow -> flow.flowId.equals(flow2));
            if (flow1InThisRoot && flow2InThisRoot) {
                ValuePair<Integer, Integer> pair = calculateDistanceAndLength(root, flow1, flow2);
                return (float) pair.value1 / pair.value2;
            } else if (flow1InThisRoot)
                flow1Found = true;
            else if (flow2InThisRoot)
                flow2Found = true;
        }

        if (flow1Found && flow2Found) return 1f; // flows belong to different roots

        String msg = "Flows don't belong to the summary:" + (flow1Found ? "" : " " + flow1) + (flow2Found ? "" : " " + flow2);
        throw new IllegalArgumentException(msg);
    }

    public FlowOutline buildOutline(String flowId, Map<String, ThreadMetadata> threads) {
        Optional<MethodCall> found = roots.stream().filter(root -> root.flows.stream().anyMatch(flow -> flow.flowId.equals(flowId))).findFirst();
        MethodCall root = found.orElseThrow(() -> new RuntimeException("Flow " + flowId + " not present in the summary."));

        return new FlowOutline(flowId, root, threads);
    }

    private ValuePair<Integer, Integer> calculateDistanceAndLength(MethodCall node, String flow1, String flow2) {
        boolean flow1Presents = node.flows.stream().anyMatch(flow -> flow.flowId.equals(flow1));
        boolean flow2Presents = node.flows.stream().anyMatch(flow -> flow.flowId.equals(flow2));

        int distance = flow1Presents == flow2Presents ? 0 : 1;
        int length = flow1Presents || flow2Presents ? 1 : 0;

        if (length > 0 && node.nestedCalls != null)
            for (MethodCall nested : node.nestedCalls) {
                ValuePair<Integer, Integer> nestedRes = calculateDistanceAndLength(nested, flow1, flow2);
                distance += nestedRes.value1;
                length += nestedRes.value2;
            }

        return new ValuePair<>(distance, length);
    }

    private void getInstrumentedMethods(MethodCall methodCall) {
        allMethods.add(new MethodConfiguration(methodCall.className, methodCall.methodName, "UNKNOWN"));
        if (methodCall.nestedCalls != null) methodCall.nestedCalls.forEach(this::getInstrumentedMethods);
    }
}
