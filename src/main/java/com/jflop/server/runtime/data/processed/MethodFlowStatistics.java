package com.jflop.server.runtime.data.processed;

import com.jflop.server.persistency.ValuePair;
import com.jflop.server.runtime.data.FlowOccurrenceData;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/7/17
 */
public class MethodFlowStatistics {

    public float throughputPerSec;
    public long minTime;
    public long maxTime;
    public long averageTime;

    public MethodFlowStatistics() {
    }

    public MethodFlowStatistics(List<ValuePair<FlowOccurrenceData.FlowElement, Float>> occurrences) {
        minTime = Long.MAX_VALUE;
        long totalTime = 0;
        int totalCount = 0;
        float totalSnapshotDurationSec = 0;
        for (ValuePair<FlowOccurrenceData.FlowElement, Float> occurrence : occurrences) {
            minTime = Math.min(minTime, occurrence.value1.minTime);
            maxTime = Math.max(maxTime, occurrence.value1.maxTime);
            totalTime += occurrence.value1.cumulativeTime / 1000000;
            totalCount += occurrence.value1.count;
            totalSnapshotDurationSec += occurrence.value2;
        }
        throughputPerSec = totalCount / totalSnapshotDurationSec;
        averageTime = totalTime / totalCount;
    }

    public void merge(MethodFlowStatistics stat) {
        this.throughputPerSec += stat.throughputPerSec;
        this.minTime = Math.min(this.minTime, stat.minTime);
        this.maxTime = Math.max(this.maxTime, stat.maxTime);
        this.averageTime = (this.averageTime + stat.averageTime) / 2;
    }
}
