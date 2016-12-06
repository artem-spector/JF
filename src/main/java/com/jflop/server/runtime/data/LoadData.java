package com.jflop.server.runtime.data;

/**
 * Represents the agent's JVM load
 *
 * @author artem on 12/5/16.
 */
public class LoadData extends RawData {

    public float processCpuLoad;
    public float heapUsed;
    public float heapCommitted;
    public float heapMax;

}
