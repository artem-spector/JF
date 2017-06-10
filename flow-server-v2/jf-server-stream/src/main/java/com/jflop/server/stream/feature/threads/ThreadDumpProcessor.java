package com.jflop.server.stream.feature.threads;

import com.jflop.server.stream.base.ProcessorState;
import com.jflop.server.stream.ext.AgentFeatureProcessor;
import com.jflop.server.stream.ext.CommandState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.jflop.features.LiveThreadsNames.LIVE_THREADS_FEATURE_ID;

/**
 * TODO: Document!
 *
 * @author artem on 01/06/2017.
 */
public class ThreadDumpProcessor extends AgentFeatureProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ThreadDumpProcessor.class);

    @ProcessorState
    private ThreadMetadataStore metadataStore;

    @ProcessorState
    private ThreadDumpStore threadDumpStore;

    public ThreadDumpProcessor() {
        super(LIVE_THREADS_FEATURE_ID, 1);
    }

    @Override
    protected void processFeatureData(Map<String, ?> data) {
        logger.info("process thread dump: " + data);
        Set<ThreadMetadata> metadata = new HashSet<>();
        List<ThreadDump> dumps = new ArrayList<>();
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            if (entry.getKey().startsWith("dump"))
                dumps.add(parseThreadDump((List<Map>) entry.getValue(), metadata));
        }

        metadataStore.putMetadata(metadata);
        threadDumpStore.putThreadDumps(dumps);
    }

    @Override
    protected void punctuateActiveAgent(long timestamp) {
        CommandState commandState = getCommandState();
        if (commandState != null && commandState.inProgress()) return;

        long lastResponse = commandState == null ? 0 : commandState.respondedAt;
        if (System.currentTimeMillis() - lastResponse > 2000)
            sendCommand("dump", null);
    }

    @Override
    public void close() {

    }

    private ThreadDump parseThreadDump(List<Map> threads, Set<ThreadMetadata> metadata) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map thread : threads) {
            List<MethodCall> stackTrace = new ArrayList<>();
            List<Map> stackTraceJson = (List<Map>) thread.get("stackTrace");
            for (Map callJson : stackTraceJson) {
                stackTrace.add(new MethodCall(
                        (String) callJson.get("className"),
                        (String) callJson.get("methodName"),
                        (String) callJson.get("fileName"),
                        (Integer) callJson.get("lineNumber")));
            }

            ThreadMetadata threadMetadata = new ThreadMetadata(Thread.State.valueOf((String) thread.get("threadState")), stackTrace);
            metadata.add(threadMetadata);
            counts.compute(threadMetadata.threadId, (key, value) -> value == null ? 1 : value++);
        }

        return new ThreadDump(counts);
    }
}
