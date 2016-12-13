package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.AgentDataFactory;
import org.jflop.config.JflopConfiguration;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
@Component
public class InstrumentationConfigurationFeature extends AgentFeature {

    private static final Logger logger = Logger.getLogger(InstrumentationConfigurationFeature.class.getName());

    public static final String FEATURE_ID = "instr-conf";
    public static final String GET_CONFIG = "get-config";
    public static final String SET_CONFIG = "set-config";

    public InstrumentationConfigurationFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(AgentJVM agentJVM, String command, String paramStr) throws ValidationException{
        switch (command) {
            case GET_CONFIG:
                return new FeatureCommand(FEATURE_ID, command, null);
            case SET_CONFIG:
                try {
                    JflopConfiguration configuration = new JflopConfiguration(new ByteArrayInputStream(paramStr.getBytes()));
                    return new FeatureCommand(FEATURE_ID, command, configuration.asJson());
                } catch (IOException e) {
                    throw new ValidationException("Invalid command parameter", e.toString());
                }
            default:
                throw new ValidationException("Invalid command", "Command " + command + " not supported by feature " + FEATURE_ID);
        }
    }

    @Override
    public List<AgentData> parseReportedData(Object dataJson, FeatureCommand command, AgentDataFactory agentDataFactory) {
        try {
            JflopConfiguration configuration = JflopConfiguration.fromJson(dataJson);
            StringWriter writer = new StringWriter();
            configuration.toProperties().store(writer, null);
            command.successText = writer.toString();
            command.progressPercent = 100;
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse instrumentation configuration");
        }
    }

    public JflopConfiguration getConfiguration(AgentJVM agentJvm) {
        FeatureCommand currentCommand = getCurrentCommand(agentJvm);
        if (currentCommand != null && currentCommand.respondedAt != null) {
            String configStr = currentCommand.successText;
            try {
                return new JflopConfiguration(new ByteArrayInputStream(configStr.getBytes("UTF-8")));
            } catch (IOException e) {
                logger.warning("Failed to read instrumentation config reported by agent.");
            }
        }

        sendCommandIfNotInProgress(agentJvm, GET_CONFIG, null);
        return null;
    }

    public void setConfiguration(AgentJVM agentJvm, JflopConfiguration configuration) {
        sendCommandIfNotInProgress(agentJvm, SET_CONFIG, configuration.asJson());
    }
}
