package com.jflop.server.stream.feature.threads;

import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ThreadMetadataStore extends AgentStateStore<TimeWindow<Set<ThreadMetadata>>> {

    public ThreadMetadataStore() {
        super("ThreadMetadataStore", 60 * 60 * 1000,
                new TypeReference<TimeWindow<Set<ThreadMetadata>>>() {
                });
    }

    public void putMetadata(Set<ThreadMetadata> metadata) {
        updateWindow(window -> window.putValue(timestamp(), metadata));
    }

    public Map<String, Set<String>> getClassMethods() {
        Set<ThreadMetadata> allMetadata = new HashSet<>();
        for (Set<ThreadMetadata> metadata : getWindow(agentJVM()).getValues(0, timestamp()).values()) {
            allMetadata.addAll(metadata);
        }

        Map<String, Set<String>> res = new HashMap<>();
        for (ThreadMetadata metadata : allMetadata) {
            for (MethodCall call : metadata.stackTrace) {
                res.computeIfAbsent(call.declaringClass, k -> new HashSet<>()).add(call.methodName);
            }
        }

        return res;
    }
}
