package com.jflop.server.stream.feature.instrumentation;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import org.jflop.config.JflopConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.jflop.features.InstrumentationConfigurationNames.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class InstrumentationConfigProcessor extends AgentFeatureProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentationConfigProcessor.class);

    @ProcessorState
    private InstrumentationConfigDataStore configDataStore;

    public InstrumentationConfigProcessor() {
        super(INSTRUMENTATION_CONFIGURATION_FEATURE_ID, 3);
    }

    @Override
    protected void processFeatureData(Map<String, ?> data) {
        logger.info("received instrumentation config: " + data);

        JflopConfiguration configuration = JflopConfiguration.fromJson(data.get(CONFIG_FIELD));
        Map<String, String> blackList = (Map<String, String>) data.get(BLACKLIST_FIELD);
        configDataStore.add(configuration, blackList);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        InstrumentationConfigData existingConfig = configDataStore.getLastConfiguration();
        CommandState command = getCommandState();
        boolean commandInProgress = command != null && command.inProgress();
        if (existingConfig == null && !commandInProgress) {
            sendCommand(GET_CONFIG_COMMAND, null);
        }
    }

    @Override
    public void close() {

    }
}
