package com.jflop.server.runtime.data.metric;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.runtime.data.FlowOccurrenceData;
import com.jflop.server.runtime.data.Metadata;
import com.jflop.server.runtime.data.ThreadOccurrenceData;
import com.jflop.server.runtime.data.metric.ValueAggregator;
import com.jflop.server.util.DigestUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem on 24/01/2017.
 */
public class MetricMetadata extends Metadata {

    public String agentJvmId;
    public long count;
    public Map<String, Long> sourceIDs;

    public static String getMetadataId(AgentJVM agentJvm) {
        return DigestUtil.uniqueId(agentJvm, "metrics");
    }

    @Override
    public String getDocumentId() {
        if (agentJvmId == null)
            agentJvmId = getMetadataId(agentJvm);
        return agentJvmId;
    }

    public void aggregateThreads(Collection<ThreadOccurrenceData> occurrences, Map<String, Float> res) {
        ValueAggregator aggregated = null;
        for (ThreadOccurrenceData occurrence : occurrences) {
            ValueAggregator curr = new ValueAggregator(occurrence.dumpId, "concurrency", true, true, true, false);
            curr.setValue(occurrence.count, occurrence.count, occurrence.count, 1, 0);
            if (aggregated == null)
                aggregated = curr;
            else
                aggregated.mergeFrom(curr);
        }

        if (aggregated != null)
            aggregated.writeMetrics(internalSourceName(aggregated), res);
    }

    public void aggregateFlows(Collection<FlowOccurrenceData> occurrences, Map<String, Float> res) {
        Map<String, ValueAggregator> aggregated = null;
        for (FlowOccurrenceData occurrence : occurrences) {
            Map<String, ValueAggregator> curr = new HashMap<>();
            getFlowValues(occurrence.rootFlow, occurrence.snapshotDurationSec, curr);
            if (aggregated == null)
                aggregated = curr;
            else
                for (Map.Entry<String, ValueAggregator> entry : curr.entrySet()) {
                    ValueAggregator aggregatedValue = aggregated.get(entry.getKey());
                    if (aggregatedValue == null)
                        aggregated.put(entry.getKey(), entry.getValue());
                    else
                        aggregatedValue.mergeFrom(entry.getValue());
                }
        }

        if (aggregated != null)
            aggregated.values().forEach(v -> v.writeMetrics(internalSourceName(v), res));
    }

    private void getFlowValues(FlowOccurrenceData.FlowElement element, float snapshotDuration, Map<String, ValueAggregator> res) {
        ValueAggregator value = new ValueAggregator(element.flowId, "flowDuration", true, true, true, true);
        value.setValue(element.cumulativeTime, element.maxTime, element.minTime, element.count, snapshotDuration);
        res.put(value.getId(), value);

        if (element.subflows != null)
            for (FlowOccurrenceData.FlowElement subflow : element.subflows)
                getFlowValues(subflow, snapshotDuration, res);
    }

    private String internalSourceName(ValueAggregator aggregator) {
        if (sourceIDs == null) sourceIDs = new HashMap<>();
        Long sourceNum = sourceIDs.computeIfAbsent(aggregator.getId(), id -> ++count);
        return String.valueOf(sourceNum);
    }

}
