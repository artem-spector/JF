package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.CpuData;
import com.jflop.server.runtime.data.RawFeatureData;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/11/16
 */
@Component
public class CpuFeature extends AgentFeature {

    public static final String FEATURE_ID = "cpu";
    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    private static final String PROCESS_CPU_LOAD = "ProcessCpuLoad";
    private static final String MESSAGE = "message";

    public CpuFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(String command, String paramStr) throws ValidationException {
        switch (command) {
            case ENABLE:
            case DISABLE:
                return new FeatureCommand(FEATURE_ID, command, null);
            default:
                throw new ValidationException("Invalid command", "Feature " + FEATURE_ID + " does not support command " + command);
        }
    }

    @Override
    public RawFeatureData parseReportedData(Object dataJson, FeatureCommand command) {
        Map json = (Map) dataJson;
        CpuData rawData = null;
        Double processCpuLoad = (Double) json.get(PROCESS_CPU_LOAD);
        String message = (String) json.get(MESSAGE);

        if (message != null)
            command.successText = message;
        else if (processCpuLoad != null) {
            command.successText = String.format("process CPU load: %.2f", processCpuLoad * 100) + "%";
            rawData = new CpuData(processCpuLoad);
        } else {
            command.successText = "Unrecognizable agent update: " + dataJson;
        }

        command.progressPercent = 100;
        return rawData;
    }

}
