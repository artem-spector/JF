package com.jflop.server.stream.feature.classinfo;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import com.jflop.server.stream.feature.threads.ThreadMetadataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jflop.features.ClassInfoNames.CLASS_INFO_FEATURE_ID;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ClassInfoProcessor extends AgentFeatureProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ClassInfoProcessor.class);

    @ProcessorState
    private ThreadMetadataStore threadMetadataStore;

    @ProcessorState
    private ClassInfoDataStore classInfoStore;

    public ClassInfoProcessor() {
        super(CLASS_INFO_FEATURE_ID, 2);
    }

    @Override
    protected void processFeatureData(Map<String, ?> data) {
        logger.info("Received class info data: " + data);
        classInfoStore.add(new ClassInfoData((Map<String, Map<String,List<String>>>) data));
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        Map<String, Set<String>> classMethods = threadMetadataStore.getClassMethods();
        if (!classMethods.isEmpty()) {
            Map<String, Set<String>> unknownMethods = classInfoStore.findUnknownMethods(classMethods);
            logger.info("unknown methods size = " + unknownMethods.size());
            if (!unknownMethods.isEmpty()) {
                CommandState cmd = getCommandState();
                if (cmd == null || !cmd.inProgress())
                    sendCommand("getDeclaredMethods", unknownMethods);
            }
        }
    }

    @Override
    public void close() {

    }
}
