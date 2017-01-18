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
        return FlowElement.representsSameFlowAs(this.rootFlow, other.rootFlow, this.rootFlow.getDistinctFlowElements(), other.rootFlow.getDistinctFlowElements());
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

        static boolean representsSameFlowAs(FlowElement a, FlowElement b, Set<FlowElement> allA, Set<FlowElement> allB) {
            int aSubflowSize = a.subflows == null ? 0 : a.subflows.size();
            int bSubflowSize = b.subflows == null ? 0 : b.subflows.size();

            // elements are equal
            if (a.equals(b)) {
                // compare common sub-elements
                int commonSize = Math.min(aSubflowSize, bSubflowSize);
                for (int i = 0; i < commonSize; i++) {
                    if (!representsSameFlowAs(a.subflows.get(i), b.subflows.get(i), allA, allB)) return false;
                }

                // one of elements may have extra sub-elements, if the other element is not aware of those extra elements at all.
                Set<FlowElement> remaining = new HashSet<>();
                // a has extra elements
                for (int i = commonSize; i < aSubflowSize; i++) {
                    remaining.addAll(a.subflows.get(i).getDistinctFlowElements());
                }
                remaining.retainAll(allB);
                if (!remaining.isEmpty()) return false;

                // b has extra elements
                for (int i = commonSize; i < bSubflowSize; i++) {
                    remaining.addAll(b.subflows.get(i).getDistinctFlowElements());
                }
                remaining.retainAll(allA);
                if (!remaining.isEmpty()) return false;

                // sub-elements fit
                return true;
            }

            // elements are not equal, try to find them in sub-elements of each other
            FlowElement foundA = a.find(b);
            if (foundA != null && representsSameFlowAs(foundA, b, allA, allB)) return true;
            FlowElement foundB = b.find(a);
            if (foundB != null && representsSameFlowAs(a, foundB, allA, allB)) return true;

            // find did not help
            return false;
        }

        private FlowElement find(FlowElement other) {
            if (this.equals(other)) return this;

            if (subflows != null)
                for (FlowElement subflow : subflows) {
                    FlowElement found = subflow.find(other);
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
