package com.jflop.server.util;

import com.jflop.server.runtime.data.processed.MethodCall;
import com.jflop.server.runtime.data.processed.MethodFlow;
import com.jflop.server.runtime.data.processed.MethodFlowStatistics;
import com.jflop.server.runtime.data.processed.ThreadHotspot;

/**
 * TODO: Document!
 *
 * @author artem on 03/01/2017.
 */
public class DebugPrintUtil {

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
