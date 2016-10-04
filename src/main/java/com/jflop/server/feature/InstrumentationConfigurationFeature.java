package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.FeatureCommand;
import org.jflop.config.JflopConfiguration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
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
    public FeatureCommand parseCommand(String command, String paramStr) throws ValidationException{
        switch (command) {
            case GET_CONFIG:
                return new FeatureCommand(FEATURE_ID, command, null);
            case SET_CONFIG:
                try {
                    return new FeatureCommand(FEATURE_ID, command, mapper.readValue(paramStr, List.class));
                } catch (IOException e) {
                    throw new ValidationException("Invalid command parameter", e.toString());
                }
            default:
                throw new ValidationException("Invalid command", "Command " + command + " not supported by feature " + FEATURE_ID);
        }
    }

    @Override
    public void updateFeatureState(FeatureCommand command, Object agentUpdate) {
        try {
            JflopConfiguration configuration = JflopConfiguration.fromJson(agentUpdate);
            StringWriter writer = new StringWriter();
            configuration.toProperties().store(writer, null);
            command.successText = writer.toString();
            command.progressPercent = 100;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse instrumentation configuration");
        }
    }

    @Override
    protected Map<String, Object> parseFeatureData(Map<String, Object> dataJson) {
        return null;
    }
}
