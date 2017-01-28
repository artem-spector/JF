package com.jflop.server.runtime.data;

import org.jflop.snapshot.Flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents statics of a specific flow in a snapshot
 *
 * @author artem
 *         Date: 12/17/16
 */
public class FlowOccurrenceData extends OccurrenceData {

    public float snapshotDurationSec;
    public FlowElement rootFlow;

    public void init(float durationSec, Flow flow) {
        snapshotDurationSec = durationSec;
        rootFlow = FlowElement.parse(flow);
    }

    @Override
    public String getMetadataId() {
        return rootFlow.flowId;
    }

    public static class FlowElement {

        public static final int NANO2MILLIS = 1000000;

        public String flowId;
        public int count;
        public long minTime;
        public long maxTime;
        public long cumulativeTime;

        public List<FlowElement> subflows;

        public static FlowElement parse(Flow flow) {
            FlowElement res = new FlowElement();
            res.flowId = flow.getKey().toString();
            res.count = flow.getCount();
            res.minTime = flow.getMinTime() / NANO2MILLIS;
            res.maxTime = flow.getMaxTime() / NANO2MILLIS;
            res.cumulativeTime = flow.getCumulativeTime() / NANO2MILLIS;

            if (flow.getSubflows() != null) {
                res.subflows = new ArrayList<>();
                for (Flow nested : flow.getSubflows()) {
                    res.subflows.add(parse(nested));
                }
            }

            return res;
        }

    }

}
