package com.jflop.server.stream.feature.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem on 02/09/2017.
 */
public class SnapshotDataStore extends AgentStateStore<TimeWindow<Map<String, Object>>> {

    public SnapshotDataStore() {
        super("SnapshotDataStore", 2 * 60 * 1000, new TypeReference<TimeWindow<Map<String, Object>>>() {
        });
    }

    public void add(Map<String, Object> json) {
        updateWindow(window -> window.putValue(timestamp(), json));
    }

    public long getLastSnapshotTime() {
        Map.Entry<Long, Map<String, Object>> entry = getWindow(agentJVM()).getLastEntry();
        return entry == null ? 0 : entry.getKey();
    }
}
