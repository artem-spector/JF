package com.jflop.server.runtime;

import com.jflop.server.runtime.data.JvmMonitorData;
import com.jflop.server.runtime.data.LiveThreadData;
import com.jflop.server.runtime.data.RawFeatureData;
import com.jflop.server.runtime.data.ThreadDumpMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchrnous data processing hub, accepts raw data, and produces/updates processed data
 * <p>
 * TODO: think of a better solution from scalability point of view
 *
 * @author artem
 *         Date: 11/26/16
 */
@Component
public class DataProcessor {

    @Autowired
    private ProcessedDataIndex processedDataIndex;

    private int threadCount = 1;
    private ExecutorService dataProcessor = Executors.newFixedThreadPool(1, r -> new Thread(r, "JFServerDataProcessor-" + threadCount++));

    public void submitJvmMonitorData(RawFeatureData rawData) {
        if (rawData instanceof JvmMonitorData)
            dataProcessor.submit((Runnable) () -> processJvmMonitorData((JvmMonitorData) rawData));
    }

    private void processJvmMonitorData(JvmMonitorData rawData) {
        String accountId = rawData.agentJvm.accountId;
        if (rawData.liveThreads != null) {
            Map<String, ThreadDumpMetadata> existing = new HashMap<>();
            for (ThreadDumpMetadata metadata : processedDataIndex.getThreadDumps(accountId)) {
                existing.put(metadata.dumpId, metadata);
            }

            for (LiveThreadData thread : rawData.liveThreads) {
                ThreadDumpMetadata threadDump = new ThreadDumpMetadata(accountId, thread.threadState, thread.stackTrace);
                if (!existing.containsKey(threadDump.dumpId)) {
                    processedDataIndex.addThreadDump(threadDump);
                    existing.put(threadDump.dumpId, threadDump);
                }
            }
        }
    }
}
