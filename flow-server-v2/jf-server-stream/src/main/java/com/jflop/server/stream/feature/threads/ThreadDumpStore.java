package com.jflop.server.stream.feature.threads;

import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ThreadDumpStore extends AgentStateStore<TimeWindow<List<ThreadDump>>> {

    public ThreadDumpStore() {
        super("ThreadDumpStore", 60 * 1000,
                new TypeReference<TimeWindow<List<ThreadDump>>>() {
                });
    }

    public void putThreadDumps(List<ThreadDump> dumps) {
        updateWindow(window -> window.putValue(timestamp(), dumps));
    }
}
