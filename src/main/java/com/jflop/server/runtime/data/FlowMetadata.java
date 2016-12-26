package com.jflop.server.runtime.data;

import org.jflop.config.NameUtils;
import org.jflop.snapshot.Flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

    public boolean fitsStacktrace(StackTraceElement[] stacktrace) {
        return flowFitsStacktrace(rootFlow, stacktrace, stacktrace.length - 1);
    }

    private static boolean flowFitsStacktrace(FlowElement flowElement, StackTraceElement[] stacktrace, int pos) {
        for (int i = pos; i >= 0; i--) {
            StackTraceElement traceElement = stacktrace[i];
            if (flowElement.fits(traceElement)) {
                if (flowElement.subflows == null || flowElement.subflows.isEmpty() || i == 0)
                    return true;
                for (FlowElement subflow : flowElement.subflows) {
                    if (flowFitsStacktrace(subflow, stacktrace, i - 1))
                        return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return rootFlow.toString();
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
                    new Object[] {className, methodName, fileName},
                    new Object[] {NameUtils.getInternalClassName(traceElement.getClassName()), traceElement.getMethodName(), traceElement.getFileName()});

            if (res) {
                int flowFirst = Integer.parseInt(firstLine);
                int flowLast = Integer.parseInt(returnLine);
                int actual = traceElement.getLineNumber();
                res = actual <= 0 || (actual >= flowFirst && actual <= flowLast);
            }

            return res;
        }

        @Override
        public String toString() {
            return "{" + className + ":" + methodName + "->[" + subflows + "]}";
        }
    }
}
