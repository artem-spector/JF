package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.RawFeatureData;
import org.jflop.snapshot.Snapshot;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/3/16
 */
@Component
public class SnapshotFeature extends AgentFeature {

    public static final String FEATURE_ID = "snapshot";
    public static final String TAKE_SNAPSHOT = "takeSnapshot";

    public SnapshotFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(String command, String paramStr) throws ValidationException {
        if (!TAKE_SNAPSHOT.equals(command))
            throw new ValidationException("Invalid command", "Command " + command + " not supported by feature " + FEATURE_ID);

        try {
            Map param = mapper.readValue(paramStr, Map.class);
            String duartionParam = "durationSec";
            Object value = param.get(duartionParam);
            if (value instanceof String)
                param.put(duartionParam, Integer.parseInt((String) value));
            return new FeatureCommand(FEATURE_ID, command, param);
        } catch (IOException e) {
            throw new ValidationException("Invalid command parameter", e.toString());
        }
    }

    @Override
    public RawFeatureData parseReportedData(Object dataJson, FeatureCommand command) {
        Map json = (Map) dataJson;
        Integer countdown = (Integer) json.get("countdown");
        if (countdown != null) {
            Integer durationSec = (Integer) ((Map) command.commandParam).get("durationSec");
            command.progressPercent = ((int) (((float) (durationSec - countdown) / durationSec) * 100));
        }

        Map<String, Object> snapshotJson = (Map<String, Object>) json.get("snapshot");
        if (snapshotJson != null) {
            command.successText = Snapshot.fromJson(snapshotJson).format(0, 0);
            command.progressPercent = 100;
        }

        return null;
    }

}
