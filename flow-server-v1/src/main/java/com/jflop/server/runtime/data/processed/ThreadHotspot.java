package com.jflop.server.runtime.data.processed;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/7/17
 */
public class ThreadHotspot {

    public String threadId;
    public String line;
    public Thread.State threadState;
    public float concurrentThreadsAvg;

    public ThreadHotspot() {
    }

    public ThreadHotspot(String threadId, String line, Thread.State threadState, float concurrentThreadsAvg) {
        this.threadId = threadId;
        this.line = line;
        this.threadState = threadState;
        this.concurrentThreadsAvg = concurrentThreadsAvg;
    }
}
