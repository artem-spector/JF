package com.jflop.server.runtime.data;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 3/4/17
 */
public class LiveThreadsData extends AgentData {

    public List liveThreads;

    public void init(List liveThreads) {
        this.liveThreads = liveThreads;
    }

}
