package com.jflop.server.util;

import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;
import com.jflop.server.runtime.data.ThreadMetadata;
import com.jflop.server.runtime.data.ThreadOccurrenceData;
import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.runtime.data.processed.MethodFlowStatistics;
import com.jflop.server.runtime.data.processed.ThreadHotspot;
import org.jflop.config.NameUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: Document!
 *
 * @author artem on 03/01/2017.
 */
public class DebugPrintUtil {

    public static String threadMetadataAndOccurrencesStr(ThreadMetadata metadata, List<ThreadOccurrenceData> occurrences) {
        String res = "thread: " + metadata.dumpId + " " + metadata.threadState + ": " + Arrays.toString(metadata.stackTrace);
        res += "\noccurrences: ";
        res += occurrences.stream().map(occurrence -> String.valueOf(occurrence.count)).collect(Collectors.joining(",", "[", "]"));
        return res;
    }

    public static String flowMetadataAndOccurrencesStr(FlowMetadata metadata, List<FlowOccurrenceData> occurrences) {
        return flowMetadataAndOccurrencesStr("", metadata.rootFlow, occurrences.stream().map(occurrence -> occurrence.rootFlow).collect(Collectors.toList()));
    }

    private static String flowMetadataAndOccurrencesStr(String indent, FlowMetadata.FlowElement flowElement, List<FlowOccurrenceData.FlowElement> occurrences) {
        String res = "\n" + indent;
        res += NameUtils.getExternalClassName(flowElement.className) + "." + flowElement.methodName + "(" + flowElement.fileName + flowElement.firstLine + ".." + flowElement.returnLine + ")";
        if (occurrences != null) {
            res += "\n" + indent + "occurrences: ";
            res += occurrences.stream().map(flow ->
                    String.format("{count: %,d; min: %,d; max: %,d; avg: %,d}", flow.count, flow.minTime / 1000000, flow.maxTime / 1000000, flow.cumulativeTime / flow.count / 1000000))
                    .collect(Collectors.joining(",", "[", "]"));
        }

        if (flowElement.subflows != null && !flowElement.subflows.isEmpty())
            for (FlowMetadata.FlowElement subflow : flowElement.subflows) {
                List<FlowOccurrenceData.FlowElement> subOccurrences = null;
                if (occurrences != null) {
                    subOccurrences = new ArrayList<>();
                    for (FlowOccurrenceData.FlowElement occurrence : occurrences) {
                        for (FlowOccurrenceData.FlowElement subElement : occurrence.subflows) {
                            if (subElement.flowId.equals(subflow.flowId)) subOccurrences.add(subElement);
                        }
                    }
                }
                res += flowMetadataAndOccurrencesStr(indent + "\t", subflow, subOccurrences);
            }
        return res;
    }

    public static String methodCallSummaryStr(String indent, MethodCall call) {
        String res = "\n" + indent + call.className + "." + call.methodName + call.methodDescriptor + " " + call.fileName + ":" + call.firstLine;
        if (call.flows != null) {
            res += "\n" + indent + "flows:";
            for (MethodFlow flow : call.flows) {
                res += "\n" + indent + "\t" + "flow: " + flow.flowId + "; return line: " + flow.returnLine;
                MethodFlowStatistics stat = flow.statistics;
                res += String.format(" stat: {throughput: %,.2f per sec; min: %,d; max: %,d; avg: %,d}", stat.throughputPerSec, stat.minTime / 1000000, stat.maxTime / 1000000, stat.averageTime / 1000000);
            }
        }
        if (call.hotspots != null) {
            res += "\n" + indent + "hotspots:";
            for (ThreadHotspot hotspot : call.hotspots) {
                res += "\n" + indent + "\t" + "thread: " + hotspot.threadId + " " + hotspot.threadState + "; line: " + hotspot.line + "; concurrency: " + hotspot.concurrentThreadsAvg;
            }
        }

        if (call.nestedCalls != null) {
            res += "\n" + indent + "nested calls:";
            for (MethodCall nestedCall : call.nestedCalls) {
                res += methodCallSummaryStr(indent + "\t", nestedCall);
            }
            res += "\n";
        }
        return res;
    }
}
