package com.jflop.server.util;

import com.jflop.server.runtime.data.*;
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
        String res = metadata.threadState + ": " + Arrays.toString(metadata.stackTrace);
        res += "\noccurrences: ";
        res += occurrences.stream().map(occurrence -> String.valueOf(occurrence.count)).collect(Collectors.joining(",", "[", "]"));
        return res;
    }

    public static String flowMetadataAndOccurrencesStr(FlowMetadata metadata, List<FlowOccurrenceData> occurrences) {
        return flowMetadataAndOccurrencesStr("", metadata.rootFlow, occurrences.stream().map(occurrence -> occurrence.rootFlow).collect(Collectors.toList()));
    }

    public static String tracedFlowMetadataAndOccurrencesStr(TracedFlowMetadata metadata, List<TracedFlowOccurrence> occurrences) {
        String res = "\n\n" + metadata.threadState + " numThreads: " + occurrences.stream().map(occurrence -> String.valueOf(occurrence.numThreads)).collect(Collectors.joining(",", "[", "]"));
        return res + flowMetadataAndOccurrencesStr("", metadata.rootFlow, occurrences.stream().map(occurrence -> occurrence.rootFlow).collect(Collectors.toList()));
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

    public static String aggregatedFlowMetadataAndOccurrenceStr(FlowMetadata metadata, AggregatedFlowOccurrence occurrence) {
        return "\n" + aggregatedFlowMetadataAndOccurrenceStr("", metadata.rootFlow, occurrence.rootFlow);
    }

    private static String aggregatedFlowMetadataAndOccurrenceStr(String indent, FlowMetadata.FlowElement metadataElement, AggregatedFlowOccurrence.AggregatedFlowElement occurrenceElement) {
        String res = "\n" + indent;
        res += NameUtils.getExternalClassName(metadataElement.className) + "." + metadataElement.methodName + "(" + metadataElement.fileName + metadataElement.firstLine + ".." + metadataElement.returnLine + ")";
        res += "\n" + indent;
        res += String.format("{throughput: %,.2f per sec; min: %,d; max: %,d; avg: %,d}", occurrenceElement.throughputPerSec, occurrenceElement.minTime / 1000000, occurrenceElement.maxTime / 1000000, occurrenceElement.averageTime / 1000000);
        res += "\n" + indent;
        if (occurrenceElement.threadStatistics != null)
            res += String.format("thread state: %s; thread concurrency: %.2f", occurrenceElement.threadStatistics.threadState, occurrenceElement.threadStatistics.threadCount);

        if (metadataElement.subflows != null && !metadataElement.subflows.isEmpty()) {
            for (FlowMetadata.FlowElement subflow : metadataElement.subflows) {
                for (AggregatedFlowOccurrence.AggregatedFlowElement subOccurrence : occurrenceElement.subflows) {
                    if (subOccurrence.flowId.equals(subflow.flowId))
                        res += aggregatedFlowMetadataAndOccurrenceStr(indent + "\t", subflow, subOccurrence);
                }
            }
        }

        return res;
    }
}
