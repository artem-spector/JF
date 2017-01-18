package com.jflop.server.runtime.data;

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

    public boolean representsSameFlowAs(FlowMetadata other) {
        Set<FlowElement> allThisElements = this.rootFlow.getDistinctFlowElements();
        Set<FlowElement> allOtherElements = other.rootFlow.getDistinctFlowElements();

        Set<FlowElement> thisCanSkip = new HashSet<>(allThisElements);
        thisCanSkip.removeAll(allOtherElements);
        Set<FlowElement> otherCanSkip = new HashSet<>(allOtherElements);
        otherCanSkip.removeAll(allThisElements);
        return FlowElement.representsSameFlowAs(this.rootFlow, other.rootFlow, thisCanSkip, otherCanSkip);
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

        static boolean representsSameFlowAs(FlowElement a, FlowElement b, Set<FlowElement> aCanSkip, Set<FlowElement> bCanSkip) {
            int aSubflowSize = a.subflows == null ? 0 : a.subflows.size();
            int bSubflowSize = b.subflows == null ? 0 : b.subflows.size();

            // elements are equal
            if (a.equals(b)) {
                // compare common sub-elements
                int commonSize = Math.min(aSubflowSize, bSubflowSize);
                for (int i = 0; i < commonSize; i++)
                    if (!representsSameFlowAs(a.subflows.get(i), b.subflows.get(i), aCanSkip, bCanSkip)) return false;

                // a has extra elements
                for (int i = commonSize; i < aSubflowSize; i++)
                    if (aCanSkip.containsAll(a.subflows.get(i).getDistinctFlowElements())) return false;

                // b has extra elements
                for (int i = commonSize; i < bSubflowSize; i++)
                    if (bCanSkip.containsAll(b.subflows.get(i).getDistinctFlowElements())) return false;

                // sub-elements fit
                return true;
            }

            // elements are not equal, try to find them in sub-elements of each other
            FlowElement foundA = a.find(b, aCanSkip);
            if (foundA != null && representsSameFlowAs(foundA, b, aCanSkip, bCanSkip)) return true;
            FlowElement foundB = b.find(a, bCanSkip);
            if (foundB != null && representsSameFlowAs(a, foundB, aCanSkip, bCanSkip)) return true;

            // find did not help
            return false;
        }

        private FlowElement find(FlowElement other, Set<FlowElement> canSkip) {
            if (this.equals(other)) return this;
            if (!canSkip.contains(this)) return null;

            if (subflows != null)
                for (FlowElement subflow : subflows) {
                    FlowElement found = subflow.find(other, canSkip);
                    if (found != null) return found;
                }

            return null;
        }

        Set<FlowElement> getDistinctFlowElements() {
            Set<FlowElement> res = new HashSet<>();
            res.add(this);
            if (subflows != null)
                for (FlowElement subflow : subflows) {
                    res.addAll(subflow.getDistinctFlowElements());
                }
            return res;
        }

        @Override
        public int hashCode() {
            return className.hashCode() + methodName.hashCode() + methodDescriptor.hashCode() + returnLine.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || !(obj instanceof FlowElement)) return false;
            FlowElement other = (FlowElement) obj;
            return className.equals(other.className) && methodName.equals(other.methodName) && methodDescriptor.equals(other.methodDescriptor)
                    && (firstLine.equals("-1") || other.firstLine.equals("-1") || firstLine.equals(other.firstLine))
                    && (returnLine.equals("-1") || other.returnLine.equals("-1") || returnLine.equals(other.firstLine));
        }
    }
}
