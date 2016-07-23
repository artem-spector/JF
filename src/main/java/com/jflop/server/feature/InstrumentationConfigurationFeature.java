package com.jflop.server.feature;

import org.jflop.config.JflopConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Get/set JFlop configuration
 *
 * @author artem
 *         Date: 7/23/16
 */
public class InstrumentationConfigurationFeature extends Feature {

    private JflopConfiguration agentConfiguration;

    public InstrumentationConfigurationFeature() {
        super("instr-conf");
    }

    @Override
    protected void processInput(Object input) {
        if (command != null) {
            // agent config received
            agentConfiguration = JflopConfiguration.fromJson(input);
            commandDone();
        }
    }

    public void requestAgentConfiguration() {
        sendCommand(new HashMap());
    }

    public void setAgentConfiguration(JflopConfiguration conf) {
        Map<String, Object> setConf = new HashMap();
        setConf.put("set-methods", conf.asJson());
        sendCommand(setConf);
    }

    public JflopConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }
}
