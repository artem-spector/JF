package com.jflop.server.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.RawFeatureData;

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

    public abstract FeatureCommand parseCommand(String command, String paramStr) throws ValidationException;

    public abstract RawFeatureData parseReportedData(Object dataJson, FeatureCommand command);

}
