package com.jflop.server.runtime.data;

import java.util.ArrayList;
import java.util.Set;

/**
 * A part of a flow observed traced by a specific thread dump
 *
 * @author artem on 03/01/2017.
 */
public class TracedFlowMetadata extends Metadata {

    public static final String DATA_TYPE = "tracedFlow";

    public String tracedFlowId;
    public Thread.State threadState;
    public FlowMetadata.FlowElement rootFlow;

    @Override
    public String getDocumentId() {
        return tracedFlowId;
    }

    public static TracedFlowMetadata getTracedFlow(FlowMetadata flowMetadata, ThreadMetadata threadMetadata, Set<StackTraceElement> instrumentedTraceElements) {
        StackTraceElement[] stackTrace = threadMetadata.stackTrace;
        FlowMetadata.FlowElement flowElement = getTracedFlowElement(flowMetadata.rootFlow, stackTrace, stackTrace.length - 1, instrumentedTraceElements);
        if (flowElement == null) return null;

        TracedFlowMetadata res = new TracedFlowMetadata();
        res.tracedFlowId = "tf-" + threadMetadata.dumpId; // TODO: it also depends on instrumented methods!
        res.threadState = threadMetadata.threadState;
        res.agentJvm = threadMetadata.agentJvm;
        res.dataType = DATA_TYPE;
        res.rootFlow = flowElement;
        return res;
    }

    private static FlowMetadata.FlowElement getTracedFlowElement(FlowMetadata.FlowElement flowElement, StackTraceElement[] stacktrace, int tracePos, Set<StackTraceElement> instrumentedTraceElements) {
        // it's ok to skip instrumented elements, if they are in the beginning of the stack
        // this is because the outmost methods might not return yet, and the registered flow may be partial.
        boolean skipInstrumented = tracePos == stacktrace.length - 1;

        // skip not fitting trace elements if they are not instrumented, or if we are in the beginning of the stack trace
        boolean fit = false;
        while (tracePos >= 0) {
            fit = flowElement.fits(stacktrace[tracePos]);
            if (!fit && (skipInstrumented || !instrumentedTraceElements.contains(stacktrace[tracePos])))
                tracePos--;
            else
                break;
        }

        // if no fitting element found in the stacktrace, it's not fit
        if (!fit) return null;

        // if we've reached the trace end, it's a fit
        if (tracePos == 0) return copy(flowElement);

        // if we've reached the flow end, its's a fit only if all the remaining methods are not instrumented
        if (flowElement.subflows == null || flowElement.subflows.isEmpty()) {
            while (--tracePos >= 0) {
                if (instrumentedTraceElements.contains(stacktrace[tracePos])) return null;
            }
            return copy(flowElement);
        }

        // if one of subflows fits the rest of the trace, it's a fit
        for (FlowMetadata.FlowElement subflow : flowElement.subflows) {
            FlowMetadata.FlowElement subelement = getTracedFlowElement(subflow, stacktrace, tracePos - 1, instrumentedTraceElements);
            if (subelement != null) {
                FlowMetadata.FlowElement res = copy(flowElement);
                res.subflows.add(subelement);
                return res;
            }
        }

        // if none of subflows fit, it's not fit
        return null;
    }

    private static FlowMetadata.FlowElement copy(FlowMetadata.FlowElement src) {
        FlowMetadata.FlowElement res = new FlowMetadata.FlowElement();
        res.flowId = src.flowId;
        res.className = src.className;
        res.methodName = src.methodName;
        res.methodDescriptor = src.methodDescriptor;
        res.fileName = src.fileName;
        res.firstLine = src.firstLine;
        res.returnLine = src.returnLine;
        res.subflows = new ArrayList<>();
        return res;
    }
}
