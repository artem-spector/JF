package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.*;
import org.jflop.snapshot.Flow;
import org.jflop.snapshot.Snapshot;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/3/16
 */
@Component
public class SnapshotFeature extends AgentFeature {

    private static final Logger logger = Logger.getLogger(SnapshotFeature.class.getName());

    public static final String FEATURE_ID = "snapshot";
    public static final String TAKE_SNAPSHOT = "takeSnapshot";
    public static final String DURATION_SEC = "durationSec";
    public static final String SNAPSHOT_FIELD = "snapshot";

    public SnapshotFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(AgentJVM agentJVM, String command, String paramStr) throws ValidationException {
        if (!TAKE_SNAPSHOT.equals(command))
            throw new ValidationException("Invalid command", "Command " + command + " not supported by feature " + FEATURE_ID);

        try {
            Map param = mapper.readValue(paramStr, Map.class);
            Object value = param.get(DURATION_SEC);
            if (value instanceof String)
                param.put(DURATION_SEC, Integer.parseInt((String) value));
            return new FeatureCommand(FEATURE_ID, command, param);
        } catch (IOException e) {
            throw new ValidationException("Invalid command parameter", e.toString());
        }
    }

    @Override
    public List<AgentData> parseReportedData(Object dataJson, FeatureCommand command, AgentDataFactory agentDataFactory) {
        Map json = (Map) dataJson;
        Integer countdown = (Integer) json.get("countdown");
        if (countdown != null) {
            Integer durationSec = (Integer) ((Map) command.commandParam).get(DURATION_SEC);
            command.progressPercent = ((int) (((float) (durationSec - countdown) / durationSec) * 100));
        }

        Map<String, Object> snapshotJson = (Map<String, Object>) json.get(SNAPSHOT_FIELD);
        if (snapshotJson == null) return null;

        Snapshot snapshot = Snapshot.fromJson(snapshotJson);
        float durationSec = (float)(snapshot.getEndTime() - snapshot.getStartTime()) / 1000;
        command.successText = snapshot.format(0, 0);
        command.progressPercent = 100;

        List configJson = (List) json.get(InstrumentationConfigurationFeature.CONFIG);

        List<AgentData> res = new ArrayList<>();
        Map<Flow.Key, Flow> flows = snapshot.getFlowMap();
        if (!flows.isEmpty()) {
            SnapshotData snapshotData = agentDataFactory.createInstance(SnapshotData.class);
            snapshotData.init(snapshot);
            res.add(snapshotData);
            for (Flow flow : flows.values()) {
                FlowMetadata metadata = agentDataFactory.createInstance(FlowMetadata.class);
                metadata.init(flow, configJson);
                res.add(metadata);
                FlowOccurrenceData occurrence = agentDataFactory.createInstance(FlowOccurrenceData.class);
                occurrence.init(durationSec, flow);
                res.add(occurrence);
            }
        }
        return res;
    }

    public boolean takeSnapshot(AgentJVM agentJvm, int durationSec) {
        Map<String, Object> param = new HashMap<>();
        param.put(DURATION_SEC, durationSec);
        boolean commandSent = sendCommandIfNotInProgress(agentJvm, TAKE_SNAPSHOT, param);
        if (commandSent)
            logger.fine("Taking snapshot (" + durationSec + ")");
        return commandSent;
    }

    public String getLastSnapshot(AgentJVM agentJvm) {
        FeatureCommand command = getCurrentCommand(agentJvm);
        if (command != null && command.progressPercent == 100) return command.successText;
        return null;
    }
}
