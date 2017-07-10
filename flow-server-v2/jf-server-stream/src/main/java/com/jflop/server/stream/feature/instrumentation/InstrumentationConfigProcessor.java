package com.jflop.server.stream.feature.instrumentation;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import com.jflop.server.stream.feature.classinfo.ClassInfoDataStore;
import com.jflop.server.stream.feature.threads.ThreadMetadataStore;
import com.jflop.server.util.ClassNameUtil;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

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

    @ProcessorState
    private ThreadMetadataStore threadMetadataStore;

    @ProcessorState
    private ClassInfoDataStore classInfoStore;

    public InstrumentationConfigProcessor() {
        super(INSTRUMENTATION_CONFIGURATION_FEATURE_ID, 3);
    }

    @Override
    protected void processFeatureData(Map<String, ?> inData) {
        logger.info("received instrumentation config: " + inData);

        InstrumentationConfigData configData = new InstrumentationConfigData();
        JflopConfiguration jflopConfiguration = JflopConfiguration.fromJson(inData.get(CONFIG_FIELD));
        for (MethodConfiguration methodConfiguration : jflopConfiguration.getAllMethods()) {
            configData.addMethodConfiguration(methodConfiguration);
        }

        Map<String, String> blackList = (Map<String, String>) inData.get(BLACKLIST_FIELD);
        if (blackList != null) {
            for (Map.Entry<String, String> entry : blackList.entrySet()) {
                String externalClassName = ClassNameUtil.replaceSlashWithDot(entry.getKey());
                configData.blackListClass(externalClassName, entry.getValue());
            }
        }

        configDataStore.add(configData);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        Set<MethodConfiguration> existingConfig = configDataStore.getLastConfiguration();
        if (existingConfig != null) {
            Set<MethodConfiguration> expectedConfig = buildExpectedConfig();
            if (expectedConfig != null && !existingConfig.containsAll(expectedConfig)) {
                JflopConfiguration jflopConfiguration = new JflopConfiguration();
                for (MethodConfiguration methodConfiguration : expectedConfig) {
                    jflopConfiguration.addMethodConfig(methodConfiguration);
                }
                sendCommandIfNotInProgress(SET_CONFIG_COMMAND, jflopConfiguration.asJson());
            }
        } else {
            sendCommandIfNotInProgress(GET_CONFIG_COMMAND, null);
        }
    }

    @Override
    public void close() {
    }

    /**
     * Builds instrumentation configuration for all instrumentable methods kept in thread metadata store.
     * If class info store does not contain all the necessary data, return null.
     *
     * @return instrumentation config or null if not all class data is known
     */
    private Set<MethodConfiguration> buildExpectedConfig() {
        Map<String, Set<String>> classMethods = threadMetadataStore.getClassMethods();
        classMethods.keySet().removeAll(configDataStore.getBlacklistedExternalClassNames());

        return classInfoStore.findMethodSignatures(classMethods);
    }

    private void sendCommandIfNotInProgress(String commandName, Object param) {
        CommandState command = getCommandState();
        logger.info("current instrumentation command: " + command);
        if (command == null || !command.inProgress()) {
            logger.info("sending instrumentation command " + commandName + "->" + param);
            sendCommand(commandName, param);
        }
    }
}
