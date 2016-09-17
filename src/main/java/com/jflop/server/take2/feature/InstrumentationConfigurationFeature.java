package com.jflop.server.take2.feature;

import org.springframework.stereotype.Component;

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
    public FeatureCommand parseCommand(String command, Map<String, Object> paramJson) {
        return null;
    }

    @Override
    protected FeatureCommand parseCommandUpdate(Map<String, Object> updateJson) {
        return null;
    }

    @Override
    protected Map<String, Object> parseFeatureData(Map<String, Object> dataJson) {
        return null;
    }
}
