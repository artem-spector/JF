package com.jflop.server.runtime.data.processed;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 1/7/17
 */
public class MethodFlow {

    public String flowId;
    public int position;
    public String returnLine;
    public MethodFlowStatistics statistics;

    public MethodFlow() {
    }

    public MethodFlow(String flowId, int position, String returnLine, MethodFlowStatistics flowStatistics) {
        this.flowId = flowId;
        this.position = position;
        this.returnLine = returnLine;
        this.statistics = flowStatistics;
    }

    @Override
    public int hashCode() {
        return flowId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof MethodFlow)) return false;
        MethodFlow that = (MethodFlow) obj;
        return this.flowId.equals(that.flowId);
    }
}
