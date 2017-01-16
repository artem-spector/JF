package com.jflop.server.runtime.data.processed;

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

    public MethodFlowStatistics(List<FlowOccurrenceData.FlowElement> occurrences, long intervalMillis) {
        minTime = Long.MAX_VALUE;
        long totalTime = 0;
        int totalCount = 0;
        for (FlowOccurrenceData.FlowElement occurrence : occurrences) {
            minTime = Math.min(minTime, occurrence.minTime);
            maxTime = Math.max(maxTime, occurrence.maxTime);
            totalTime += occurrence.cumulativeTime;
            totalCount += occurrence.count;
        }
        float timeSec = (float) intervalMillis / 1000;
        throughputPerSec = totalCount / timeSec;
        averageTime = totalTime / totalCount;
    }

    public void merge(MethodFlowStatistics stat) {
        this.throughputPerSec += stat.throughputPerSec;
        this.minTime = Math.min(this.minTime, stat.minTime);
        this.maxTime = Math.max(this.maxTime, stat.maxTime);
        this.averageTime = (this.averageTime + stat.averageTime) / 2;
    }
}
