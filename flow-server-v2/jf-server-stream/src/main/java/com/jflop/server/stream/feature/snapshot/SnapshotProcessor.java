package com.jflop.server.stream.feature.snapshot;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import com.jflop.server.stream.feature.instrumentation.InstrumentationConfigDataStore;
import org.jflop.config.MethodConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.jflop.features.SnapshotFeatureNames.*;

/**
 * TODO: Document!
 *
 * @author artem on 02/09/2017.
 */
public class SnapshotProcessor extends AgentFeatureProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotProcessor.class);

    @ProcessorState
    private SnapshotDataStore snapshotDataStore;

    @ProcessorState
    private InstrumentationConfigDataStore configDataStore;

    public SnapshotProcessor() {
        super(SNAPSHOT_FEATURE_ID, 5);
    }

    @Override
    protected void processFeatureData(Map<String, ?> data) {
        logger.info("Received snapshot: " + data);

        Map<String, Object> snapshotJson = (Map<String, Object>) data.get(SNAPSHOT_FIELD);
        if (snapshotJson != null)
            snapshotDataStore.add(snapshotJson);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        Set<MethodConfiguration> configuration = configDataStore.getLastConfiguration();
        if (configuration == null || configuration.isEmpty()) return;

        long gap = System.currentTimeMillis() - snapshotDataStore.getLastSnapshotTime();
        if (gap > 5000) {
            Map<String, Object> param = new HashMap<>();
            param.put(DURATION_SEC_FIELD, 2);
            sendCommandIfNotInProgress(TAKE_SNAPSHOT_COMMAND, param);
        }
    }

    @Override
    public void close() {
    }

    private void sendCommandIfNotInProgress(String commandName, Object param) {
        CommandState command = getCommandState();
        logger.info("current snapshot command: " + command);
        if (command == null || !command.inProgress()) {
            logger.info("sending snapshot command " + commandName + "->" + param);
            sendCommand(commandName, param);
        }
    }
}
