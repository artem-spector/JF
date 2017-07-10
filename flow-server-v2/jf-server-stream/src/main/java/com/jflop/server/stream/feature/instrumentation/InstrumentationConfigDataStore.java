package com.jflop.server.stream.feature.instrumentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class InstrumentationConfigDataStore extends AgentStateStore<TimeWindow<InstrumentationConfigData>> {

    public InstrumentationConfigDataStore() {
        super("InstrumentationConfigDataStore", 2 * 60 * 1000, new TypeReference<TimeWindow<InstrumentationConfigData>>() {});
    }

    public void add(InstrumentationConfigData data) {
        updateWindow(window -> window.putValue(timestamp(), data));
    }

    public InstrumentationConfigData getLastConfiguration() {
        return getWindow(agentJVM()).getLastValue();
    }
}
