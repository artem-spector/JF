package com.jflop.server.runtime.data;

import org.jflop.config.NameUtils;
import org.jflop.snapshot.Flow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Unique flow metadata
 *
 * @author artem on 15/12/2016.
 */
public class FlowMetadata extends Metadata {

    public FlowElement rootFlow;

    public void init(Flow flow) {
        rootFlow = FlowElement.parse(flow);
    }

    @Override
    public String getDocumentId() {
        return rootFlow.flowId;
    }

    public boolean fitsStacktrace(StackTraceElement[] stacktrace, Set<StackTraceElement> instrumentedTraceElements) {
        return flowFitsStacktrace(rootFlow, stacktrace, stacktrace.length - 1, instrumentedTraceElements);
    }

    private static boolean flowFitsStacktrace(FlowElement flowElement, StackTraceElement[] stacktrace, int tracePos, Set<StackTraceElement> instrumentedTraceElements) {
        // skip not fitting trace elements if they are not instrumented
        boolean fit = false;
        while (tracePos >= 0) {
            fit = flowElement.fits(stacktrace[tracePos]);
            if (!fit && !instrumentedTraceElements.contains(stacktrace[tracePos]))
                tracePos--;
            else
                break;
        }

        // if no fitting element found in the stacktrace, it's not fit
        if (!fit) return false;

        // if we've reached the trace end, it's a fit
        if (tracePos == 0) return true;

        // if we've reached the flow end, its's a fit only if all the remaining methods are not instrumented
        if (flowElement.subflows == null || flowElement.subflows.isEmpty()) {
            while (--tracePos >= 0) {
                if (instrumentedTraceElements.contains(stacktrace[tracePos])) return false;
            }
            return true;
        }

        // if one of subflows fits the rest of the trace, it's a fit
        for (FlowElement subflow : flowElement.subflows) {
            if (flowFitsStacktrace(subflow, stacktrace, tracePos - 1, instrumentedTraceElements))
                return true;
        }

        // if none of subflows fit, it's not fit
        return false;
    }

    @Override
    public String toString() {
        return rootFlow.toString(0);
    }

    public static class FlowElement {

        public String flowId;
        public String className;
        public String fileName;
        public String methodName;
        public String methodDescriptor;
        public String firstLine;
        public String returnLine;

        public List<FlowElement> subflows;

        public static FlowElement parse(Flow flow) {
            FlowElement res = new FlowElement();
            res.flowId = flow.getKey().toString();
            res.className = flow.className;
            res.fileName = flow.fileName;
            res.methodName = flow.methodName;
            res.methodDescriptor = flow.methodDescriptor;
            res.firstLine = flow.firstLine;
            res.returnLine = flow.returnLine;

            Collection<Flow> subflows = flow.getSubflows();
            if (subflows != null) {
                res.subflows = new ArrayList<>(subflows.size());
                res.subflows.addAll(subflows.stream().map(FlowElement::parse).collect(Collectors.toList()));
            }
            return res;
        }

        public boolean fits(StackTraceElement traceElement) {
            boolean res = Arrays.equals(
                    new Object[]{className, methodName, fileName},
                    new Object[]{NameUtils.getInternalClassName(traceElement.getClassName()), traceElement.getMethodName(), traceElement.getFileName()});

            if (res) {
                int flowFirst = Integer.parseInt(firstLine);
                int flowLast = Integer.parseInt(returnLine);
                int actual = traceElement.getLineNumber();
                res = actual <= 0 || (actual >= flowFirst && actual <= flowLast);
            }

            return res;
        }

        public String toString(int indent) {
            String res = "\n";
            for (int i = 0; i < indent; i++) res += "\t";
            res += "(" + flowId + ") ";
            res += NameUtils.getExternalClassName(className) + "." + methodName + "(" + fileName + firstLine + ".." + returnLine + ")";
            if (subflows != null && !subflows.isEmpty())
                for (FlowElement subflow : subflows) {
                    res += subflow.toString(indent + 1);
                }
            return res;
        }
    }
}
