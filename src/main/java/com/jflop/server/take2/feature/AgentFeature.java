package com.jflop.server.take2.feature;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
public abstract class AgentFeature {

    public final String featureId;

    protected ObjectMapper mapper = new ObjectMapper();

    protected AgentFeature(String featureId) {
        this.featureId = featureId;
    }

    public abstract FeatureCommand parseCommand(String command, Map<String, Object> paramJson);

    protected abstract FeatureCommand parseCommandUpdate(Map<String, Object> updateJson);

    protected abstract Map<String, Object> parseFeatureData(Map<String, Object> dataJson);

}
