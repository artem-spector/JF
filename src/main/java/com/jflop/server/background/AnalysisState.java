package com.jflop.server.background;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jflop.config.JflopConfiguration;

import java.util.Date;

/**
 * State of analysis task
 *
 * @author artem on 09/01/2017.
 */
public class AnalysisState {

    @JsonProperty
    Date processedUntil;

    @JsonProperty
    int snapshotDuration;

    @JsonProperty
    private Object instrumentationJson;

    @JsonIgnore
    private JflopConfiguration instrumentationCache;

    public AnalysisState() {
    }

    public static AnalysisState createState() {
        AnalysisState state = new AnalysisState();
        state.processedUntil = new Date();
        state.snapshotDuration = 1;
        return state;
    }

    @JsonIgnore
    public JflopConfiguration getInstrumentationConfig() {
        if (instrumentationJson != null && instrumentationCache == null)
            instrumentationCache = JflopConfiguration.fromJson(instrumentationJson);
        return instrumentationCache;
    }

    @JsonIgnore
    public void setInstrumentationConfig(JflopConfiguration instr) {
        this.instrumentationCache = instr;
        instrumentationJson = instr.asJson();
    }
}
