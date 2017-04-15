package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jflop.config.MethodConfiguration;
import org.jflop.snapshot.Flow;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unique flow metadata
 *
 * @author artem on 15/12/2016.
 */
public class FlowMetadata extends Metadata {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List instrumentedMethodsJson;

    @JsonIgnore
    public FlowElement rootFlow;

    public void init(Flow flow, List instrumentedMethodsJson) {
        rootFlow = FlowElement.parse(flow);
        this.instrumentedMethodsJson = instrumentedMethodsJson;
    }

    @Override
    public String getDocumentId() {
        return rootFlow.flowId;
    }

    @JsonProperty
    public String getRootFlowStr() throws JsonProcessingException {
        return MAPPER.writeValueAsString(rootFlow);
    }

    @JsonProperty
    public void setRootFlowStr(String str) throws IOException {
        rootFlow = MAPPER.readValue(str, FlowElement.class);
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
            FlowElement copy;
            copy = new FlowElement();
            copy.className = className;
            copy.methodName = methodName;
            copy.methodDescriptor = methodDescriptor;
            copy.firstLine = firstLine;
            copy.returnLine = returnLine;

            if (subflows != null) {
                copy.subflows = new ArrayList<>(subflows.size());
                subflows.forEach(subflow -> copy.subflows.add(subflow.deepCopy()));
            }
            return copy;
        }

        boolean deepEquals(FlowElement other) {
            if (!this.equals(other)) return false;

            int thisNested = subflows == null ? 0 : subflows.size();
            int otherNested = other.subflows == null ? 0 : other.subflows.size();

            if (thisNested != otherNested) return false;

            for (int i = 0; i < thisNested; i++)
                if (!subflows.get(i).deepEquals(other.subflows.get(i))) return false;

            return true;
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
            return className.hashCode() + methodName.hashCode() + methodDescriptor.hashCode();
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
