package com.jflop.server.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JF Agent registration record
 *
 * @author artem
 *         Date: 7/2/16
 */
public class JFAgent {

    public String agentId;
    public String accountId;
    public String name;
    public long lastReportTime;

    @SuppressWarnings("unused") // for JSON deserialization
    public JFAgent() {
    }

    public JFAgent(String accountId, String name) {
        this.agentId = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.name = name;
    }

    public List<Map<String, Object>> reportFeaturesAndGetTasks(Map<String, Object> featuresData) {
        lastReportTime = System.currentTimeMillis();
        return new ArrayList<>();
    }
}
