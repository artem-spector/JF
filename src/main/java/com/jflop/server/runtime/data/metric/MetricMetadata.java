package com.jflop.server.runtime.data.metric;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.runtime.data.FlowOccurrenceData;
import com.jflop.server.runtime.data.LoadData;
import com.jflop.server.runtime.data.Metadata;
import com.jflop.server.runtime.data.ThreadOccurrenceData;
import com.jflop.server.util.DigestUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

    public void aggregateLoad(List<LoadData> dataList, Map<String, Float> res) {
        Aggregator aggregator = new Aggregator();
        for (LoadData loadData : dataList) {
            ValueAggregator cpu = new ValueAggregator("load", "cpu", true, true, true, false);
            cpu.setValue(loadData.processCpuLoad, loadData.processCpuLoad, loadData.processCpuLoad, 1, -1);
            aggregator.add(cpu);
            ValueAggregator heap = new ValueAggregator("load", "mem", true, true, true, false);
            heap.setValue(loadData.heapUsed, loadData.heapUsed, loadData.heapUsed, 1, -1);
            aggregator.add(heap);
        }

        aggregator.writeTo(res);
    }

    public void aggregateThreads(Collection<ThreadOccurrenceData> occurrences, Map<String, Float> res) {
        Aggregator aggregator = new Aggregator();
        for (ThreadOccurrenceData occurrence : occurrences) {
            ValueAggregator value = new ValueAggregator("thread", occurrence.threadState.name(), true, true, true, false);
            value.setValue(occurrence.count, occurrence.count, occurrence.count, 1, 0);
            aggregator.add(value);
        }
        aggregator.writeTo(res);
    }

    public void aggregateFlows(Collection<FlowOccurrenceData> occurrences, Map<String, Float> res) {
        Aggregator aggregator = new Aggregator();
        for (FlowOccurrenceData occurrence : occurrences) {
            getFlowValues(occurrence.rootFlow, occurrence.snapshotDurationSec, aggregator);
        }
        aggregator.writeTo(res);
    }

    private void getFlowValues(FlowOccurrenceData.FlowElement element, float snapshotDuration, Aggregator aggregator) {
        ValueAggregator value = new ValueAggregator(element.flowId, "flowDuration", true, true, true, true);
        value.setValue(element.cumulativeTime, element.maxTime, element.minTime, element.count, snapshotDuration);
        aggregator.add(value);

        if (element.subflows != null)
            for (FlowOccurrenceData.FlowElement subflow : element.subflows)
                getFlowValues(subflow, snapshotDuration, aggregator);
    }

    private String internalSourceName(ValueAggregator aggregator) {
        if (sourceIDs == null) sourceIDs = new HashMap<>();
        Long sourceNum = sourceIDs.computeIfAbsent(aggregator.getId(), id -> ++count);
        return String.valueOf(sourceNum);
    }

    private class Aggregator {

        private Map<String, ValueAggregator> res = new HashMap<>();

        public void add(ValueAggregator value) {
            String key = value.getId();
            ValueAggregator existing = res.get(key);
            if (existing == null)
                res.put(key, value);
            else
                existing.mergeFrom(value);
        }

        public void writeTo(Map<String, Float> out) {
            res.values().forEach(v -> v.writeMetrics(internalSourceName(v), out));
        }
    }
}
