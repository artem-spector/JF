package com.jflop.server.runtime.data;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/12/16
 */
public class JvmMonitorData extends RawFeatureData {

    public double processCpuLoad;
    public List<LiveThreadData> liveThreads;

}
