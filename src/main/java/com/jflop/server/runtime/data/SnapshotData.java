package com.jflop.server.runtime.data;

import org.jflop.snapshot.Snapshot;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem on 27/02/2017.
 */
public class SnapshotData extends AgentData {

    public Map<String, Object> snapshotJson;

    public void init(Snapshot snapshot) {
        snapshotJson = snapshot.asJson();
    }
}
