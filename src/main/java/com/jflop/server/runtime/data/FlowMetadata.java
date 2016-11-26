package com.jflop.server.runtime.data;

/**
 * Information about a flow
 *
 * @author artem
 *         Date: 11/26/16
 */
public class FlowMetadata {

    public String accountId;

    private ThreadStacktrace stacktrace;
    private Object configJson;

    public FlowMetadata(String accountId, ThreadStacktrace stacktrace) {
        this.accountId = accountId;
        this.stacktrace = stacktrace;
    }

    public boolean covers(ThreadStacktrace stacktrace) {
        return this.stacktrace.getCommonPathLength(stacktrace) >= stacktrace.stackTrace.length;
    }
}
