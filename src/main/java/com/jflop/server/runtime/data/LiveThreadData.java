package com.jflop.server.runtime.data;

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

    public static LiveThreadData fromJson(Map<String, Object> json) {
        LiveThreadData res = new LiveThreadData();
        res.threadId = Long.parseLong(String.valueOf(json.get("threadId")));
        res.threadName = (String) json.get("threadName");
        res.threadState = Thread.State.valueOf((String) json.get("threadState"));
        return res;
    }
}
