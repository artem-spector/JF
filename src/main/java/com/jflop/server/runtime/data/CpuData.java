package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/12/16
 */
public class CpuData extends RawFeatureData {

    public double processCpuLoad;

    public CpuData() {
    }

    public CpuData(double processCpuLoad) {
        this.processCpuLoad = processCpuLoad;
    }
}
