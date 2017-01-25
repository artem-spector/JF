package com.jflop.server.runtime.data;

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

    @Override
    public String getDocumentId() {
        if (agentJvmId == null)
            agentJvmId = DigestUtil.uniqueId(agentJvm, "metrics");
        return agentJvmId;
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
