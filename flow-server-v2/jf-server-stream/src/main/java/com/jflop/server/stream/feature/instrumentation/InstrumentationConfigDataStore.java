package com.jflop.server.stream.feature.instrumentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import org.jflop.config.JflopConfiguration;

import java.util.Map;

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

    public void add(JflopConfiguration configuration, Map<String, String> blackList) {
        InstrumentationConfigData data = new InstrumentationConfigData();

        for (String className : configuration.getClassNames()) {
            data.addInstrumentedClass(className, configuration.getMethods(className));
        }

        if (blackList != null) {
            for (Map.Entry<String, String> entry : blackList.entrySet()) {
                data.blackListClass(entry.getKey(), entry.getValue());
            }

        }

        updateWindow(window -> window.putValue(timestamp(), data));
    }

    public InstrumentationConfigData getLastConfiguration() {
        return getWindow(agentJVM()).getLastValue();
    }
}
