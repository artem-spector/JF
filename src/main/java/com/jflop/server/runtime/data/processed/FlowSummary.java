package com.jflop.server.runtime.data.processed;

import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.*;

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

    public void aggregateFlows(Map<FlowMetadata, List<FlowOccurrenceData>> flows) {
        roots = new ArrayList<>();
        for (Map.Entry<FlowMetadata, List<FlowOccurrenceData>> entry : flows.entrySet()) {
            FlowMetadata flowMetadata = entry.getKey();
            List<FlowOccurrenceData> occurrences = entry.getValue();
            MethodCall call = MethodCall.getOrCreateCall(roots, flowMetadata.rootFlow);
            call.addFlow(flowMetadata, occurrences);
        }
    }

    public void aggregateThreads(Map<ThreadMetadata, List<ThreadOccurrenceData>> threads, Set<StackTraceElement> instrumentedTraceElements) {
        for (MethodCall root : roots) {
            for (Map.Entry<ThreadMetadata, List<ThreadOccurrenceData>> entry : threads.entrySet()) {
                ThreadMetadata threadMetadata = entry.getKey();
                StackTraceElement[] stackTrace = threadMetadata.stackTrace;
                if (stackTrace != null && stackTrace.length > 0) {
                    List<ThreadOccurrenceData> threadOccurrences = entry.getValue();
                    List<ValuePair<MethodCall, Integer>> path = new ArrayList<>();
                    if (findPath(root, stackTrace, stackTrace.length - 1, instrumentedTraceElements, path)) {
                        root.addThread(path, threadOccurrences);
                    }
                }
            }
        }
    }

    public static boolean findPath(MethodCall methodCall, StackTraceElement[] stacktrace, int tracePos, Set<StackTraceElement> instrumentedTraceElements, List<ValuePair<MethodCall, Integer>> path) {
        // it's ok to skip instrumented elements, if they are in the beginning of the stack
        // this is because the outmost methods might not return yet, and the registered flow may be partial.
        boolean skipInstrumented = tracePos == stacktrace.length - 1;

        // skip not fitting trace elements if they are not instrumented, or if we are in the beginning of the stack trace
        boolean fit = false;
        while (tracePos >= 0) {
            fit = methodCall.fits(stacktrace[tracePos]);
            if (!fit && (skipInstrumented || !instrumentedTraceElements.contains(stacktrace[tracePos])))
                tracePos--;
            else
                break;
        }

        // if no fitting element found in the stacktrace, it's not fit
        if (!fit) return false;

        // if the flow element fits the stacktrace element, add the method call and the line number to the path
        path.add(new ValuePair<>(methodCall, stacktrace[tracePos].getLineNumber()));

        // if we've reached the trace end, it's a fit
        if (tracePos == 0) return true;

        // if we've reached the flow end, its's a fit only if all the remaining methods are not instrumented
        if (methodCall.nestedCalls == null || methodCall.nestedCalls.isEmpty()) {
            while (--tracePos >= 0) {
                if (instrumentedTraceElements.contains(stacktrace[tracePos])) return false;
            }
            return true;
        }

        // if one of subflows fits the rest of the trace, it's a fit
        for (MethodCall nested : methodCall.nestedCalls) {
            if (findPath(nested, stacktrace, tracePos - 1, instrumentedTraceElements, path))
                return true;
        }

        // if none of subflows fit, it's not fit
        return false;
    }
}
