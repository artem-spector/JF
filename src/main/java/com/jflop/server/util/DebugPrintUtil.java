package com.jflop.server.util;

import com.jflop.server.runtime.data.processed.*;

/**
 * TODO: Document!
 *
 * @author artem on 03/01/2017.
 */
public class DebugPrintUtil {

    public static String printFlowSummary(FlowSummary flowSummary, boolean expanded) {
        String res = "Flow summary of " + flowSummary.time + " contains " + flowSummary.roots.size() + " roots";
        if (expanded) {
            res += "\n-------- Flow summary content ---------";
            for (MethodCall root : flowSummary.roots) {
                res += DebugPrintUtil.methodCallSummaryStr("", root);
            }
            res += "\n-----------------------------------";
        }
        return res + "\n";
    }

    private static String methodCallSummaryStr(String indent, MethodCall call) {
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
        }
        return res;
    }
}
