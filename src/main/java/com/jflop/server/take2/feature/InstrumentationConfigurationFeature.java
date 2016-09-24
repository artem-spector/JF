package com.jflop.server.take2.feature;

import com.jflop.server.take2.admin.data.FeatureCommand;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
@Component
public class InstrumentationConfigurationFeature extends AgentFeature {

    public static final String FEATURE_ID = "instr-conf";
    public static final String GET_CONFIG = "get-config";
    public static final String SET_CONFIG = "set-config";

    public InstrumentationConfigurationFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(String command, Object param) {
        switch (command) {
            case GET_CONFIG:
                return new FeatureCommand(FEATURE_ID, command, null);
            case SET_CONFIG:
                return new FeatureCommand(FEATURE_ID, command, new HashMap<>());
            default:
                throw new RuntimeException("Invalid command: " + command);
        }
    }

    @Override
    public FeatureCommand parseCommandUpdate(Object update) {
        return null;
    }

    @Override
    protected Map<String, Object> parseFeatureData(Map<String, Object> dataJson) {
        return null;
    }
}
