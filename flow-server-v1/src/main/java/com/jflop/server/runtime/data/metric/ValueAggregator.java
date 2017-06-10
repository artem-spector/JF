package com.jflop.server.runtime.data.metric;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/28/17
 */
public class ValueAggregator {

    private String metadataId;
    private String dataName;
    private String throughputName;

    private float total;
    private int count;
    private float time;

    ValueAggregator(String metadataId, String dataName, String throughputName) {
        this.metadataId = metadataId;
        this.dataName = dataName;
        this.throughputName = throughputName;
    }

    public String getId() {
        return metadataId + ":" + dataName;
    }

    void setValue(float total, int count, float time) {
        this.total = total;
        this.count = count;
        this.time = time;
    }

    void mergeFrom(ValueAggregator other) {
        assert other != null && other.metadataId.equals(metadataId) && other.dataName.equals(dataName);

        this.total += other.total;
        this.count += other.count;
    }

    void writeMetrics(String sourceId, Map<String, Float> out) {
        out.put(dataName + "_" + sourceId, total / count);
        if (throughputName != null)
            out.put(throughputName + "_" + sourceId, (float) count / time);
    }

}
