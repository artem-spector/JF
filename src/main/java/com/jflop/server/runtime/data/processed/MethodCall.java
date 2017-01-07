package com.jflop.server.runtime.data.processed;

import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/7/17
 */
public class MethodCall {

    public String className;
    public String fileName;
    public String methodName;
    public String methodDescriptor;
    public String firstLine;

    public List<MethodCall> nestedCalls;

    public List<MethodFlow> flows;

    public List<ThreadHotspot> hotspots;

    public MethodCall() {
    }

    public MethodCall(FlowMetadata.FlowElement flow) {
        this.className = flow.className;
        this.fileName = flow.fileName;
        this.methodName = flow.methodName;
        this.methodDescriptor = flow.methodDescriptor;
        this.firstLine = flow.firstLine;
    }

    public void addFlow(FlowMetadata metadata, List<FlowOccurrenceData> occurrences, long intervalLengthMillis) {
        List<FlowOccurrenceData.FlowElement> occurrenceElements = occurrences.stream().map(occ -> occ.rootFlow).collect(Collectors.toList());
        addFlowRecursively(metadata.rootFlow.flowId, metadata.rootFlow, occurrenceElements, intervalLengthMillis);
    }

    private void addFlowRecursively(String flowId, FlowMetadata.FlowElement metadata, List<FlowOccurrenceData.FlowElement> occurrences, long intervalLengthMillis) {
        if (flows == null) flows = new ArrayList<>();
        MethodFlowStatistics stat = new MethodFlowStatistics(occurrences, intervalLengthMillis);
        flows.add(new MethodFlow(flowId, metadata.returnLine, stat));

        if (metadata.subflows == null) return;
        if (nestedCalls == null) nestedCalls = new ArrayList<>();

        for (int i = 0; i < metadata.subflows.size(); i++) {
            final int subflowIdx = i;
            FlowMetadata.FlowElement subMeta = metadata.subflows.get(subflowIdx);
            MethodCall nestedCall = getOrCreateCall(nestedCalls, subMeta);

            List<FlowOccurrenceData.FlowElement> subOccurrences = occurrences.stream().map(occurrence -> occurrence.subflows.get(subflowIdx)).collect(Collectors.toList());
            nestedCall.addFlowRecursively(flowId, subMeta, subOccurrences, intervalLengthMillis);
        }
    }

    public static MethodCall getOrCreateCall(List<MethodCall> list, FlowMetadata.FlowElement metadata) {
        MethodCall call = new MethodCall(metadata);
        int pos = list.indexOf(call);
        if (pos == -1)
            list.add(call);
        else
            call = list.get(pos);
        return call;
    }

    @Override
    public int hashCode() {
        return className.hashCode() + fileName.hashCode() + methodName.hashCode() + methodDescriptor.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof MethodCall)) return false;
        MethodCall that = (MethodCall) obj;
        return Arrays.equals(new Object[] {className, fileName, methodName, methodDescriptor},
                new Object[] {that.className, that.fileName, that.methodName, that.methodDescriptor});
    }
}
