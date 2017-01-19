package com.jflop.server.runtime.data;

import org.jflop.config.MethodConfiguration;
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
    public List instrumentedMethodsJson;

    public void init(Flow flow, List instrumentedMethodsJson) {
        rootFlow = FlowElement.parse(flow);
        this.instrumentedMethodsJson = instrumentedMethodsJson;
    }

    @Override
    public String getDocumentId() {
        return rootFlow.flowId;
    }

    /**
     * Checks whether the given flows may be the same if reduced to a common instrumentation configuration.<br/>
     * A positive answer might be false positive, for example if the two flows have no common instrumentation.<br/>
     * A negative answer is true negative, i.e. the flows are different after the reduction.<br/>
     * The relation is symmetric, the order of parameters does not matter.
     *
     * @param a one flow, not null
     * @param b another flow, not null
     * @return true if the flows may be the same, false if they are definitely different.
     */
    public static boolean maybeSame(FlowMetadata a, FlowMetadata b) {
        List commonInstrumentation = new ArrayList(a.instrumentedMethodsJson);
        commonInstrumentation.retainAll(b.instrumentedMethodsJson);
        if (commonInstrumentation.isEmpty()) return true;

        FlowElement aReduced = a.reduce(commonInstrumentation);
        FlowElement bReduced = b.reduce(commonInstrumentation);

        return aReduced.deepEquals(bReduced);
    }

    private FlowElement reduce(List reduceConfig) {
        List collapseMethods = new ArrayList(instrumentedMethodsJson);
        collapseMethods.removeAll(reduceConfig);
        Set<FlowElement> collapseElements = new HashSet<>();
        collapseMethods.forEach(mtd -> collapseElements.add(FlowElement.fromMethodConfigurationJson(mtd)));

        FlowElement res = rootFlow.deepCopy();
        res.collapse(collapseElements);
        return res;
    }

    @Override
    public boolean mergeTo(Metadata existing) {
        return ((FlowMetadata) existing).instrumentedMethodsJson.retainAll(instrumentedMethodsJson);
    }

    public static class FlowElement implements Cloneable {

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

        public static FlowElement fromMethodConfigurationJson(Object json) {
            MethodConfiguration conf = MethodConfiguration.fromJson((Map<String, Object>) json);
            FlowElement res = new FlowElement();
            res.className = conf.internalClassName;
            res.methodName = conf.methodName;
            res.methodDescriptor = conf.methodDescriptor;
            res.firstLine = "-1";
            res.returnLine = "-1";
            return res;
        }

        FlowElement deepCopy() {
            FlowElement clone;
            try {
                clone = (FlowElement) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }

            if (subflows != null) {
                clone.subflows = new ArrayList<>(subflows.size());
                for (FlowElement element : subflows)
                    clone.subflows.add(element.deepCopy());
            }
            return clone;
        }

        boolean deepEquals(FlowElement other) {
            return this.equals(other) && this.subflows.equals(other.subflows);
        }

        List<FlowElement> collapse(Set<FlowElement> collapseElements) {
            if (subflows != null && !subflows.isEmpty()) {
                List<FlowElement> newSubflows = new ArrayList<>();
                for (FlowElement subflow : subflows) {
                    List<FlowElement> collapsedSubflows = subflow.collapse(collapseElements);
                    if (collapsedSubflows != null) {
                        for (FlowElement collapsedSubflow : collapsedSubflows) {
                            if (newSubflows.isEmpty())
                                newSubflows.add(collapsedSubflow);
                            else {
                                FlowElement lastSubflow = newSubflows.get(newSubflows.size() - 1);
                                if (lastSubflow.equals(collapsedSubflow))
                                    lastSubflow.addSubflows(collapsedSubflow.subflows);
                                else
                                    newSubflows.add(collapsedSubflow);
                            }
                        }
                    }
                }
                subflows = newSubflows;
            }

            if (collapseElements.contains(this))
                return subflows;
            else
                return Collections.singletonList(this);
        }

        private void addSubflows(List<FlowElement> additional) {
            if (additional == null || additional.isEmpty()) return;
            if (subflows == null) subflows = new ArrayList<>();

            for (FlowElement element : additional) {
                int pos = subflows.indexOf(element);
                if (pos == -1)
                    subflows.add(element);
                else
                    subflows.get(pos).addSubflows(element.subflows);
            }

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
                    && (returnLine.equals("-1") || other.returnLine.equals("-1") || returnLine.equals(other.returnLine));
        }
    }
}
