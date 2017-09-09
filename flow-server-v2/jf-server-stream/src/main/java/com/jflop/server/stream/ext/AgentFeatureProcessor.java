package com.jflop.server.stream.ext;

import com.jflop.server.data.AgentJVM;
import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.base.ProcessorTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.jflop.server.stream.ext.AgentFeatureProcessor.*;
import static org.jflop.features.CommonFeatureNames.ERROR_FIELD;
import static org.jflop.features.CommonFeatureNames.PROGRESS_FIELD;

/**
 * Maintains the command state and allows sending commands to the agent
 *
 * @author artem on 22/05/2017.
 */
@ProcessorTopology(parentSources = {INPUT_SOURCE_ID}, childSinks = {COMMANDS_SINK_ID, DB_INGEST_SINK_ID})
public abstract class AgentFeatureProcessor extends AgentProcessor<Map<String, Map<String, Object>>> {

    private static final Logger logger = LoggerFactory.getLogger(AgentFeatureProcessor.class);

    public static final String INPUT_SOURCE_ID = "AgentInput";
    public static final String COMMANDS_SINK_ID = "OutgoingCommands";
    public static final String DB_INGEST_SINK_ID = "DBIngestData";

    public final String featureId;

    @ProcessorState
    private CommandStateStore commands;

    protected AgentFeatureProcessor(String featureId, int punctuationIntervalSec) {
        super(featureId + "-processor", punctuationIntervalSec);
        this.featureId = featureId;
    }

    @Override
    public void process(AgentJVM agentJVM, Map<String, Map<String, Object>> features) {
        logger.debug(getClass().getSimpleName() + ".process(" + agentJVM + ")");
        super.process(agentJVM, features);
        Map<String, Object> featureData = features.get(featureId);
        if (featureData != null) {
            parseCommand(featureData);
            CommandState commandState = getCommandState();
            String error = commandState == null ? null : commandState.error;
            if (error == null)
                processFeatureData(featureData);
            else
                logger.error("Feature " + featureId + " error: " + error);
        }
        context.commit();
    }

    protected CommandState getCommandState() {
        return commands.getCommandState(featureId);
    }

    protected void sendCommand(String command, Object param) {
        CommandState cmd = new CommandState();
        cmd.command = command;
        cmd.sentAt = System.currentTimeMillis();
        commands.setCommandState(featureId, cmd);

        Map<String, Object> featureCmd = new HashMap<>();
        featureCmd.put(command, param);
        Map<String, Object> agentCmd = new HashMap<>();
        agentCmd.put("feature", featureId);
        agentCmd.put("command", featureCmd);
        context.forward(agentJVM, agentCmd, COMMANDS_SINK_ID);
    }

    protected void sendDataToDB(String docType, Map json) {
        json.put("docType", docType);
        context.forward(agentJVM, json, DB_INGEST_SINK_ID);
    }

    private void parseCommand(Map<String, Object> featureData) {
        logger.debug("parse command: " + featureData);
        Integer progress = (Integer) featureData.remove(PROGRESS_FIELD);
        String error = (String) featureData.remove(ERROR_FIELD);
        if (progress != null || error != null) {
            CommandState cmd = getCommandState();
            if (cmd == null) cmd = new CommandState();
            if (progress != null) cmd.progress = progress;
            if (error != null) cmd.error = error;
            cmd.respondedAt = context.timestamp();
            commands.setCommandState(featureId, cmd);
        }
    }

    protected abstract void processFeatureData(Map<String, ?> data);
}
