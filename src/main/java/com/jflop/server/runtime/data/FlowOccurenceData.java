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
public class FlowOccurenceData extends OccurrenceData {

    public FlowElement rootFlow;

    public void init(Flow flow) {
        rootFlow = FlowElement.parse(flow);
    }

    @Override
    public String getMetadataId() {
        return rootFlow.flowId;
    }

    static class FlowElement {

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
            res.minTime = flow.getMinTime();
            res.maxTime = flow.getMaxTime();
            res.cumulativeTime = flow.getCumulativeTime();

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
