package com.jflop.server.stream.feature.instrumentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import org.jflop.config.MethodConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class InstrumentationConfigDataStore extends AgentStateStore<TimeWindow<InstrumentationConfigData>> {

    public InstrumentationConfigDataStore() {
        super("InstrumentationConfigDataStore", 2 * 60 * 1000, new TypeReference<TimeWindow<InstrumentationConfigData>>() {
        });
    }

    public void add(InstrumentationConfigData data) {
        updateWindow(window -> window.putValue(timestamp(), data));
    }

    public Set<MethodConfiguration> getLastConfiguration() {
        InstrumentationConfigData configData = getWindow(agentJVM()).getLastValue();
        return configData == null ? null : configData.getMethodConfigurations();
    }

    public Set<String> getBlacklistedExternalClassNames() {
        Set<String> res = new HashSet<>();
        for (InstrumentationConfigData configData : getWindow(agentJVM()).getValues(0, timestamp()).values()) {
            res.addAll(configData.getBlacklistedExternalClassNames());
        }
        return res;
    }
}
