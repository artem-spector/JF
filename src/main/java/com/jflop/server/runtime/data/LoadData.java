package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;

/**
 * Represents the agent's JVM load
 *
 * @author artem on 12/5/16.
 */
public class LoadData extends RawData {

    public static final String TYPE = "load";

    public double processCpuLoad;

    public LoadData() {
    }

    public LoadData(double processCpuLoad) {
        this.processCpuLoad = processCpuLoad;
    }
}
