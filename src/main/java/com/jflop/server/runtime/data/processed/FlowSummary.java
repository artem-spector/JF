package com.jflop.server.runtime.data.processed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.*;
import org.jflop.config.MethodConfiguration;
import org.jflop.config.NameUtils;

import java.util.*;

/**
 * Contains processed data of a flow with its sub-flows and related stack traces within a time interval.
 * The {@link #time} field contains the beginning of the interval
 *
 * @author artem
 *         Date: 1/7/17
 */
public class FlowSummary extends AgentData {

    public List<MethodCall> roots;

    @JsonIgnore
    private Set<MethodConfiguration> allMethods;

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
                    List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
                    if (findPath(root, stackTrace, stackTrace.length - 1, path)) {
                        root.addThread(path, threadOccurrences);
                    }
                }
            }
        }
    }

    public boolean findPath(MethodCall methodCall, StackTraceElement[] stacktrace, int tracePos, List<ValuePair<MethodCall, Integer>> path) {
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
            return false;

        // if the flow element fits the stacktrace element, add the method call and the line number to the path
        path.add(new ValuePair<>(methodCall, stacktrace[tracePos].getLineNumber()));

        // if we've reached the trace end, it's a fit
        if (tracePos == 0) return true;

        // if we've reached the flow end, its's a fit only if all the remaining methods are not instrumented
        if (methodCall.nestedCalls == null || methodCall.nestedCalls.isEmpty()) {
            while (--tracePos >= 0) {
                if (isInstrumented(stacktrace[tracePos])) return false;
            }
            return true;
        }

        // if one of subflows fits the rest of the trace, it's a fit
        for (MethodCall nested : methodCall.nestedCalls) {
            if (findPath(nested, stacktrace, tracePos - 1, path))
                return true;
        }

        // if none of subflows fit, it's not fit
        return false;
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
