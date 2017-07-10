package com.jflop.server.stream.feature.instrumentation;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import com.jflop.server.stream.feature.classinfo.ClassInfoDataStore;
import com.jflop.server.stream.feature.threads.ThreadMetadataStore;
import org.jflop.config.JflopConfiguration;
import org.jflop.config.MethodConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
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
                configData.blackListClass(entry.getKey(), entry.getValue());
            }
        }

        configDataStore.add(configData);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        InstrumentationConfigData existingConfig = configDataStore.getLastConfiguration();
        if (existingConfig != null) {
            InstrumentationConfigData expectedConfig = buildExpectedConfig();
            if (!existingConfig.covers(expectedConfig)) {
                sendCommandIfNotInProgress(SET_CONFIG_COMMAND, (Map<String, ?>) toJflopConfiguration(expectedConfig).asJson());
            }
        } else {
            sendCommandIfNotInProgress(GET_CONFIG_COMMAND, null);
        }
    }

    @Override
    public void close() {

    }

    private JflopConfiguration toJflopConfiguration(InstrumentationConfigData expectedConfig) {
        JflopConfiguration jflopConfiguration = new JflopConfiguration();
        for (Map.Entry<String, ClassInstrumentationData> entry : expectedConfig.instrumentedClasses.entrySet()) {
            String internalClassName = entry.getKey();
            for (Map.Entry<String, List<String>> methodEntry : entry.getValue().methodSignatures.entrySet()) {
                String methodName = methodEntry.getKey();
                for (String descriptor : methodEntry.getValue()) {
                    jflopConfiguration.addMethodConfig(new MethodConfiguration(internalClassName, methodName, descriptor));
                }
            }
        }
        return jflopConfiguration;
    }

    private InstrumentationConfigData buildExpectedConfig() {
        InstrumentationConfigData res = new InstrumentationConfigData();

        Map<String, Set<String>> methodSignatures = classInfoStore.findMethodSignatures(threadMetadataStore.getClassMethods());
        for (Map.Entry<String, Set<String>> entry : methodSignatures.entrySet()) {
            String className = entry.getKey();
            for (String signature : entry.getValue()) {
                res.addMethodConfiguration(new MethodConfiguration(className + "." + signature));
            }
        }

        return res;
    }

    private void sendCommandIfNotInProgress(String commandName, Map<String, ?> param) {
        CommandState command = getCommandState();
        if (command == null || !command.inProgress()) {
            logger.info("sending instrumentation command " + commandName);
            sendCommand(commandName, param);
        }
    }
}
