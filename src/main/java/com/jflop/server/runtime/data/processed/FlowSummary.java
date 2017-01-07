package com.jflop.server.runtime.data.processed;

import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.FlowOccurrenceData;

import java.util.*;

/**
 * Contains processed data of a flow with its sub-flows and related stack traces within a time interval.
 * The {@link #time} field contains the beginning of the interval
 *
 * @author artem
 *         Date: 1/7/17
 */
public class FlowSummary extends AgentData {

    public long intervalLengthMillis;

    public List<MethodCall> roots;

    public void aggregateFlows(Map<FlowMetadata, List<FlowOccurrenceData>> flows, long intervalLengthMillis) {
        this.intervalLengthMillis = intervalLengthMillis;

        roots = new ArrayList<>();
        for (FlowMetadata flowMetadata : flows.keySet()) {
            MethodCall call = MethodCall.getOrCreateCall(roots, flowMetadata.rootFlow);
            call.addFlow(flowMetadata, flows.get(flowMetadata), intervalLengthMillis);
        }
    }

}
