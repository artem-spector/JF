package com.jflop.server.feature;

import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.background.JvmMonitorAnalysis;
import com.jflop.server.runtime.data.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/11/16
 */
@Component
public class JvmMonitorFeature extends AgentFeature {

    private static final Logger logger = Logger.getLogger(JvmMonitorFeature.class.getName());

    public static final String FEATURE_ID = "jvmMonitor";
    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    private static final String PROCESS_CPU_LOAD = "processCpuLoad";
    private static final String LIVE_THREADS = "liveThreads";
    private static final String HEAP_MEMORY_USAGE = "heapUsage";
    private static final String MESSAGE = "message";

    @Autowired
    private JvmMonitorAnalysis analysisTask;

    public JvmMonitorFeature() {
        super(FEATURE_ID);
    }

    @Override
    public FeatureCommand parseCommand(AgentJVM agentJVM, String command, String paramStr) throws ValidationException {
        switch (command) {
            case ENABLE:
                analysisTask.start(agentJVM);
                return new FeatureCommand(FEATURE_ID, ENABLE, null);
            case DISABLE:
                analysisTask.stop(agentJVM);
                return new FeatureCommand(FEATURE_ID, DISABLE, null);
            default:
                throw new ValidationException("Invalid command", "Feature " + FEATURE_ID + " does not support command " + command);
        }
    }

    @Override
    public List<AgentData> parseReportedData(Object dataJson, FeatureCommand command, AgentDataFactory agentDataFactory) {
        Map json = (Map) dataJson;
        String message = (String) json.get(MESSAGE);
        if (message == null) message = "";
        List<AgentData> res = new ArrayList<>();

        // JVM load
        Double processCpuLoad = (Double) json.get(PROCESS_CPU_LOAD);
        Map heapMemoryUsage = (Map) json.get(HEAP_MEMORY_USAGE);
        LoadData data = null;
        if (processCpuLoad != null || heapMemoryUsage != null) {
            data = agentDataFactory.createInstance(LoadData.class);
            res.add(data);
        }
        if (processCpuLoad != null) {
            data.processCpuLoad = processCpuLoad.floatValue() * 100;
            message += "\n" + String.format("process CPU load: %.2f", data.processCpuLoad) + "%";
        }
        if (heapMemoryUsage != null) {
            data.heapUsed = intToFloat(heapMemoryUsage.get("used")) / 1000000;
            data.heapCommitted = intToFloat(heapMemoryUsage.get("committed")) / 1000000;
            data.heapMax = intToFloat(heapMemoryUsage.get("max")) / 1000000;
            message += "\n" + String.format("Heap usage (MB): %,.1f of %,.1f, max %,.1f", data.heapUsed, data.heapCommitted, data.heapMax);
        }

        // threads
        List liveThreads = (List) json.get(LIVE_THREADS);
        if (liveThreads != null) {
            message += "\n" + liveThreads.size() + " live threads";

            Map<String, ThreadOccurrenceData> occurrences = new HashMap<>();
            for (Object thread : liveThreads) {
                ThreadMetadata threadMetadata = agentDataFactory.createInstance(ThreadMetadata.class);
                try {
                    threadMetadata.read((Map<String, Object>) thread);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Ignore failed parsing stacktrace: " + thread);
                    continue;
                }

                ThreadOccurrenceData occurrenceData = occurrences.get(threadMetadata.dumpId);
                if (occurrenceData == null) {
                    res.add(threadMetadata);
                    occurrenceData = agentDataFactory.createInstance(ThreadOccurrenceData.class);
                    occurrenceData.dumpId = threadMetadata.dumpId;
                    occurrenceData.threadState = threadMetadata.threadState;
                    occurrenceData.instrumentable = threadMetadata.instrumentable;
                    occurrenceData.count = 1;
                    occurrences.put(occurrenceData.dumpId, occurrenceData);
                } else {
                    occurrenceData.count++;
                }
            }
            res.addAll(occurrences.values());
        }

        command.successText = message;
        command.progressPercent = 100;
        return res;
    }

    private float intToFloat(Object val) {
        if (val instanceof Integer) return ((Integer) val).floatValue();
        if (val instanceof Long) return ((Long) val).floatValue();
        throw new RuntimeException("Cannot convert " + val.getClass().getName() + " to float.");
    }

}
