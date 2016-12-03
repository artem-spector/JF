package com.jflop.server.runtime.data;

import java.util.List;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/12/16
 */
public class LiveThreadData {

    public long threadId;
    public String threadName;
    public Thread.State threadState;
    public StackTraceElement[] stackTrace;

    public static LiveThreadData fromJson(Map<String, Object> json) {
        LiveThreadData res = new LiveThreadData();
        res.threadId = Long.parseLong(String.valueOf(json.get("threadId")));
        res.threadName = (String) json.get("threadName");
        res.threadState = Thread.State.valueOf((String) json.get("threadState"));

        List<Map<String, Object>> stackTraceJson = (List<Map<String, Object>>) json.get("stackTrace");
        res.stackTrace = new StackTraceElement[stackTraceJson.size()];
        for (int i = 0; i < stackTraceJson.size(); i++) {
            Map<String, Object> elementJson = stackTraceJson.get(i);
            res.stackTrace[i] = new StackTraceElement(
                    (String) elementJson.get("className"),
                    (String) elementJson.get("methodName"),
                    (String) elementJson.get("fileName"),
                    (Integer) elementJson.get("lineNumber"));
        }
        return res;
    }
}
