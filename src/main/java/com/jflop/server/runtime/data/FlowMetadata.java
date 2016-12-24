package com.jflop.server.runtime.data;

import org.jflop.config.NameUtils;
import org.jflop.snapshot.Flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

    public boolean fitsExpectedFlow(List<FlowElement> expectedFlow) {
        return fitsSubflow(rootFlow, expectedFlow, 0);
    }

    private boolean fitsSubflow(FlowElement flow, List<FlowElement> expectedFlow, int pos) {
        for (int i = pos; i < expectedFlow.size(); i++) {
            FlowElement expectedElement = expectedFlow.get(i);
            if (flow.fitsExpected(expectedElement)) {
                if (flow.subflows == null)
                    return true;
                for (FlowElement subflow : flow.subflows) {
                    if (fitsSubflow(subflow, expectedFlow, i + 1))
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
                for (Flow nested : subflows) {
                    res.subflows.add(parse(nested));
                }
            }
            return res;
        }

        public static FlowElement expectedFlowElement(StackTraceElement element) {
            FlowElement res = new FlowElement();
            res.fileName = element.getFileName();
            res.firstLine = String.valueOf(element.getLineNumber());
            res.className = NameUtils.getInternalClassName(element.getClassName());
            res.methodName = element.getMethodName();
            return res;
        }

        public boolean fitsExpected(FlowElement expected) {
            boolean res = Arrays.equals(
                    new Object[] {className, methodName, fileName},
                    new Object[] {expected.className, expected.methodName, expected.fileName});

            if (res) {
                int flowFirst = Integer.parseInt(firstLine);
                int flowLast = Integer.parseInt(returnLine);
                int actual = Integer.parseInt(expected.firstLine);
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
