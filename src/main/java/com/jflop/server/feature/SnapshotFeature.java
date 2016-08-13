package com.jflop.server.feature;

import org.jflop.snapshot.Snapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Take snapshot
 *
 * @author artem
 *         Date: 7/23/16
 */
public class SnapshotFeature extends Feature {

    public static final String NAME = "snapshot";
    public static final String TAKE_SNAPSHOT = "takeSnapshot";

    private Integer durationSec;
    private Snapshot lastSnapshot;

    public SnapshotFeature() {
        super(NAME);
    }

    @Override
    protected void processInput(Object input) {
        Map json = (Map) input;

        Map<String, Object> snapshotJson = (Map<String, Object>) json.get("snapshot");
        if (snapshotJson != null) {
            this.lastSnapshot = Snapshot.fromJson(snapshotJson);
            commandDone();
        }

        Integer countdown = (Integer) json.get("countdown");
        if (countdown != null) {
            setCommandProgress((durationSec - countdown) / 100);
        }
    }

    public void takeSnapshot(Integer durationSec) {
        this.durationSec = durationSec;
        sendCommand(TAKE_SNAPSHOT, durationSec);
    }

    public Snapshot getLastSnapshot() {
        if (getError() != null) throw new RuntimeException(getError());
        return lastSnapshot;
    }

    @Override
    protected Map<String, Object> getState() {
        Map<String, Object> res = new HashMap<>();
        if (lastSnapshot != null) {
            res.put("duration", durationSec);
            res.put("snapshot", lastSnapshot.toString());
        }
        return res;
    }
}
