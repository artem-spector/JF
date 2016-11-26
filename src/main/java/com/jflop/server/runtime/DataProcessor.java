package com.jflop.server.runtime;

import com.jflop.server.runtime.data.FlowMetadata;
import com.jflop.server.runtime.data.JvmMonitorData;
import com.jflop.server.runtime.data.LiveThreadData;
import com.jflop.server.runtime.data.ThreadStacktrace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchrnous data processing hub, accepts raw data, and produces/updates processed data
 *
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

    public void submitJvmMonitorData(JvmMonitorData rawData) {
        dataProcessor.submit((Runnable) () -> processJvmMonitorData(rawData));
    }

    private void processJvmMonitorData(JvmMonitorData rawData) {
        String accountId = rawData.agentJvm.accountId;
        if (rawData.liveThreads != null) {
            Set<ThreadStacktrace> uniqueStacktraces = new HashSet<>();
            for (LiveThreadData thread : rawData.liveThreads) {
                uniqueStacktraces.add(thread.asStacktrace());
            }

            List<FlowMetadata> flows = processedDataIndex.getFlows(accountId);
            for (Iterator<ThreadStacktrace> iterator = uniqueStacktraces.iterator(); iterator.hasNext();) {
                ThreadStacktrace stacktrace = iterator.next();
                boolean hasFlow = false;
                for (FlowMetadata flow : flows) {
                    if (flow.covers(stacktrace)) {
                        hasFlow = true;
                        break;
                    }
                }
                if (!hasFlow) {
                    new FlowMetadata(accountId, stacktrace);
                }
            }
        }
    }
}
