package com.jflop.server.runtime.data.processed;

import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;
import com.jflop.server.runtime.data.ThreadOccurrenceData;
import org.jflop.config.NameUtils;

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

    public String className; // internal one
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

    public static MethodCall getOrCreateCall(List<MethodCall> list, FlowMetadata.FlowElement metadata) {
        MethodCall call = new MethodCall(metadata);
        int pos = list.indexOf(call);
        if (pos == -1)
            list.add(call);
        else
            call = list.get(pos);
        return call;
    }

    public void addFlow(FlowMetadata metadata, List<FlowOccurrenceData> occurrences) {
        List<ValuePair<FlowOccurrenceData.FlowElement, Float>> occurrenceElements =
                occurrences.stream().map(occ -> new ValuePair<>(occ.rootFlow, occ.snapshotDurationSec)).collect(Collectors.toList());
        addFlowRecursively(metadata.rootFlow.flowId, metadata.rootFlow, 0, occurrenceElements);
    }

    public void addThread(List<ValuePair<MethodCall, Integer>> path, List<ThreadOccurrenceData> occurrences) {
        ThreadOccurrenceData first = occurrences.get(0);
        String threadId = first.dumpId;
        Thread.State threadState = first.threadState;
        float countAvg = 0;
        for (ThreadOccurrenceData occurrence : occurrences) {
            countAvg += occurrence.count;
        }
        countAvg = countAvg / occurrences.size();

        addThreadRecursively(path, 0, threadId, threadState, countAvg);
    }

    public boolean fits(StackTraceElement traceElement) {
        boolean res = Arrays.equals(
                new Object[]{className, methodName, fileName},
                new Object[]{NameUtils.getInternalClassName(traceElement.getClassName()), traceElement.getMethodName(), traceElement.getFileName()});

        if (res) {
            int flowFirst = Integer.parseInt(firstLine);
            int flowLast = flows.stream().mapToInt(flow -> Integer.parseInt(flow.returnLine)).max().getAsInt();
            int actual = traceElement.getLineNumber();
            res = actual <= 0 || (actual >= flowFirst && actual <= flowLast);
        }

        return res;
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

    private void addFlowRecursively(String flowId, FlowMetadata.FlowElement metadata, int position, List<ValuePair<FlowOccurrenceData.FlowElement, Float>> occurrences) {
        if (flows == null) flows = new ArrayList<>();
        MethodFlowStatistics stat = new MethodFlowStatistics(occurrences);
        MethodFlow methodFlow = new MethodFlow(flowId, position, metadata.returnLine, stat);

        int parentPosition = 0;
        int existingPos = flows.indexOf(methodFlow);
        if (existingPos == -1) {
            flows.add(methodFlow);
        } else {
            MethodFlow existing = flows.get(existingPos);
            existing.statistics.merge(stat);
            existing.position += position;
            parentPosition = existing.position;
        }

        if (metadata.subflows == null) return;
        if (nestedCalls == null) nestedCalls = new ArrayList<>();

        for (int i = 0; i < metadata.subflows.size(); i++) {
            final int subflowIdx = i;
            FlowMetadata.FlowElement subMeta = metadata.subflows.get(subflowIdx);
            MethodCall nestedCall = getOrCreateCall(nestedCalls, subMeta);

            List<ValuePair<FlowOccurrenceData.FlowElement, Float>> subOccurrences =
                    occurrences.stream().map(occurrence -> new ValuePair<>(occurrence.value1.subflows.get(subflowIdx), occurrence.value2)).collect(Collectors.toList());
            nestedCall.addFlowRecursively(flowId, subMeta, parentPosition + subflowIdx, subOccurrences);
        }
    }

    private void addThreadRecursively(List<ValuePair<MethodCall, Integer>> path, int pathPos, String threadId, Thread.State threadState, float countAvg) {
        ValuePair<MethodCall, Integer> methodLinePair = path.get(pathPos);
        assert methodLinePair.value1 == this;
        String line = String.valueOf(methodLinePair.value2);

        if (hotspots == null) hotspots = new ArrayList<>();
        hotspots.add(new ThreadHotspot(threadId, line, threadState, countAvg));

        if (pathPos < path.size() - 1) {
            pathPos++;
            ValuePair<MethodCall, Integer> next = path.get(pathPos);
            for (MethodCall nestedCall : nestedCalls) {
                if (nestedCall == next.value1) {
                    nestedCall.addThreadRecursively(path, pathPos, threadId, threadState, countAvg);
                    break;
                }
            }
        }
    }
}
