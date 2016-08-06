package com.jflop.server.feature;

import org.jflop.config.JflopConfiguration;

import java.util.*;

/**
 * Get/set JFlop configuration
 *
 * @author artem
 *         Date: 7/23/16
 */
public class InstrumentationConfigurationFeature extends Feature {

    public static final String NAME = "instr-conf";
    public static final String GET_CONFIG = "get-config";
    public static final String SET_CONFIG = "set-config";

    private JflopConfiguration agentConfiguration;

    public InstrumentationConfigurationFeature() {
        super(NAME);
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
        sendCommand(GET_CONFIG, new HashMap());
    }

    public void setAgentConfiguration(JflopConfiguration conf) {
        sendCommand(SET_CONFIG, conf.asJson());
    }

    public JflopConfiguration getAgentConfiguration() {
        if (getError() != null) throw new RuntimeException(getError());
        return agentConfiguration;
    }

    @Override
    protected Map<String, Object> getState() {
        String txt = "";
        if (agentConfiguration != null) {
            Properties properties = agentConfiguration.toProperties();
            for (String mtd : properties.stringPropertyNames()) {
                txt += mtd + "\n";
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("methods", txt);
        return res;
    }
}
