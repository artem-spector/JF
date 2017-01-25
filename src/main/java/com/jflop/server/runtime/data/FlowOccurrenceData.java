package com.jflop.server.runtime.data;

import org.jflop.snapshot.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public static class FlowElement implements MetricSource {

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

        @Override
        public String getSourceId() {
            return flowId;
        }

        @Override
        public String[] getMetricNames() {
            return new String[]{"min", "max", "avg", "thrpt"};
        }

        @Override
        public void aggregate(float elapsedTimeSec, Map<String, Float> aggregated) {
            aggregated.compute("min", (key, value) -> value == null ? minTime : Math.min(value, minTime));
            aggregated.compute("max", (key, value) -> value == null ? maxTime : Math.max(value, maxTime));
            float averageDuration = (float) cumulativeTime / count / 1000;
            float throughput = count / elapsedTimeSec;
            aggregated.compute("avg", (key, value) -> value == null ? averageDuration : (value + averageDuration) / 2);
            aggregated.compute("thrpt", (key, value) -> value == null ? throughput : (value + throughput) / 2);
        }
    }

}
