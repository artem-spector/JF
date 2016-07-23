package com.jflop.server.feature;

import org.jflop.config.JflopConfiguration;

import java.util.HashMap;

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
        sendCommand("get-config", new HashMap());
    }

    public void setAgentConfiguration(JflopConfiguration conf) {
        sendCommand("set-config", conf.asJson());
    }

    public JflopConfiguration getAgentConfiguration() {
        if (getError() != null) throw new RuntimeException(getError());
        return agentConfiguration;
    }
}
