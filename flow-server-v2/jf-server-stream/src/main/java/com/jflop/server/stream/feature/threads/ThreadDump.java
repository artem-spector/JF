package com.jflop.server.stream.feature.threads;

import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 31/05/2017
 */
public class ThreadDump {

    public Map<String, Integer> threadsCount;

    public ThreadDump() {
    }

    public ThreadDump(Map<String, Integer> threadsCount) {
        this.threadsCount = threadsCount;
    }
}
