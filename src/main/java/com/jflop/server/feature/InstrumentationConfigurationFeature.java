package com.jflop.server.feature;

import org.jflop.config.JflopConfiguration;

import java.util.HashMap;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 7/23/16
 */
public class InstrumentationConfigurationFeature extends Feature {

    public static final String FEATURE_NAME = "instr-conf";

    private JflopConfiguration agentConfiguration;

    public InstrumentationConfigurationFeature() {
        super(FEATURE_NAME);
    }

    @Override
    protected void processInput(Object input) {
        if (command != null && command.isEmpty()) {
            // get configuration command, agent config received
            agentConfiguration = JflopConfiguration.fromJson(input);
            commandDone();
        }
    }

    public void requestAgentConfiguration() {
        sendCommand(new HashMap());
    }

    public JflopConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }
}
