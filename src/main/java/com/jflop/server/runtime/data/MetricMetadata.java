package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.util.DigestUtil;

import java.util.*;

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

    public void aggregateFlowOccurrence(FlowOccurrenceData flowOccurrence, Map<String, Float> res) {
        aggregateFlowElement(flowOccurrence.rootFlow, res, flowOccurrence.snapshotDurationSec);
    }

    private void aggregateFlowElement(FlowOccurrenceData.FlowElement element, Map<String, Float> res, float snapshotDurationSec) {
        aggregate(snapshotDurationSec, element, res);
        if (element.subflows != null) {
            element.subflows.forEach(subflow -> aggregateFlowElement(subflow, res, snapshotDurationSec));
        }
    }

    public void aggregate(float elapsedTimeSec, MetricSource source, Map<String, Float> res) {
        String sourceId = source.getSourceId();
        Map<String, Float> working = new HashMap<>();
        List<String> externalNames = Arrays.asList(source.getMetricNames());
        externalNames.forEach(externalName -> working.put(externalName, res.get(toInternalName(sourceId, externalName))));

        source.aggregate(elapsedTimeSec, working);
        working.forEach((externalName, value) -> res.put(toInternalName(sourceId, externalName), value));
    }

    private String toInternalName(String sourceId, String externalName) {
        if (sourceIDs == null) sourceIDs = new HashMap<>();
        Long sourceNum = sourceIDs.computeIfAbsent(sourceId, id -> ++count);
        return String.valueOf(sourceNum) + "-" + externalName;
    }

}
