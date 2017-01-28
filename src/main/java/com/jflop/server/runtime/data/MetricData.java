package com.jflop.server.runtime.data;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/28/17
 */
public class MetricData extends OccurrenceData {

    public Map<String, Float> metrics;

    @Override
    public String getMetadataId() {
        return MetricMetadata.getMetadataId(agentJvm);
    }
}
