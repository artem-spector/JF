package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/11/16
 */
@Component
public class JvmMonitorFeature extends AgentFeature {

    public static final String FEATURE_ID = "jvmMonitor";
    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    private static final String PROCESS_CPU_LOAD = "processCpuLoad";
    private static final String LIVE_THREADS = "liveThreads";
    private static final String MESSAGE = "message";

    public JvmMonitorFeature() {
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
    public List<RawData> parseReportedData(Object dataJson, FeatureCommand command) {
        Map json = (Map) dataJson;
        Double processCpuLoad = (Double) json.get(PROCESS_CPU_LOAD);
        List liveThreads = (List) json.get(LIVE_THREADS);
        String message = (String) json.get(MESSAGE);
        if (message == null) message = "";

        List<RawData> rawData = new ArrayList<>();
        if (processCpuLoad != null) {
            message += "\n" + String.format("process CPU load: %.2f", processCpuLoad * 100) + "%";
            rawData.add(new LoadData(processCpuLoad));
        }

        if (liveThreads != null) {
            message += "\n" + liveThreads.size() + " live threads";
            for (Object thread : liveThreads) {
                rawData.add(new ThreadDumpData((Map<String, Object>) thread));
            }
        }

        command.successText = message;
        command.progressPercent = 100;
        return rawData;
    }

}
