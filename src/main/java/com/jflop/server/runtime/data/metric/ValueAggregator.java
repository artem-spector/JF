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

    private float total;
    private int count;
    private float time;
    private float min;
    private float max;

    private boolean produceAverage;
    private boolean produceMax;
    private boolean produceMin;
    private boolean produceThroughput;

    public ValueAggregator(String metadataId, String dataName, boolean produceAverage, boolean produceMax, boolean produceMin, boolean produceThroughput) {
        this.metadataId = metadataId;
        this.dataName = dataName;

        this.produceAverage = produceAverage;
        this.produceMax = produceMax;
        this.produceMin = produceMin;
        this.produceThroughput = produceThroughput;
    }

    public String getId() {
        return metadataId + ":" + dataName;
    }

    public void setValue(float total, float max, float min, int count, float time) {
        this.total = total;
        this.max = max;
        this.min = min;
        this.count = count;
        this.time = time;
    }

    public void mergeFrom(ValueAggregator other) {
        assert other != null && other.metadataId.equals(metadataId) && other.dataName.equals(dataName);

        this.total += other.total;
        this.count += other.count;
        this.min = Math.min(this.min, other.min);
        this.max = Math.min(this.max, other.max);
    }

    public void writeMetrics(String sourceId, Map<String, Float> out) {
        if (produceAverage)
            out.put(sourceId + "_" + getAverageName(), total / count);
        if (produceMax)
            out.put(sourceId + "_" + getMaxName(), max);
        if (produceMin)
            out.put(sourceId + "_" + getMinName(), min);
        if (produceThroughput)
            out.put(sourceId + "_" + getThroughputName(), (float) count / time);
    }

    private String getAverageName() {
        return dataName + "Avg";
    }

    private String getMaxName() {
        return dataName + "Max";
    }

    private String getMinName() {
        return dataName + "Min";
    }

    private String getThroughputName() {
        return "freq";
    }
}
