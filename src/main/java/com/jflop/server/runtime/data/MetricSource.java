package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem on 24/01/2017.
 */
public interface MetricSource {

    @JsonIgnore
    String getSourceId();

    @JsonIgnore
    String[] getMetricNames();

    void aggregate(float elapsedTimeSec, Map<String, Float> aggregated);

}
