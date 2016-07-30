package com.jflop.server.admin;

import com.jflop.server.feature.Feature;
import com.jflop.server.feature.InstrumentationConfigurationFeature;
import com.jflop.server.feature.SnapshotFeature;

import java.util.*;

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
    public Date lastReportTime;

    private Feature[] features = {new InstrumentationConfigurationFeature(), new SnapshotFeature()};

    @SuppressWarnings("unused") // for JSON deserialization
    public JFAgent() {
    }

    public JFAgent(String accountId, String name) {
        this.agentId = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.name = name;
    }

    public List<Map<String, Object>> reportFeaturesAndGetTasks(Map<String, Object> featuresData) {
        lastReportTime = new Date();
        ArrayList<Map<String, Object>> res = new ArrayList<>();
        for (Feature feature : features) {
            Map<String, Object> command = feature.poll(featuresData.get(feature.name));
            if (command != null) {
                Map<String, Object> wrappedCommand = new HashMap<>();
                wrappedCommand.put("feature", feature.name);
                wrappedCommand.put("command", command);
                res.add(wrappedCommand);
            }
        }

        return res;
    }

    public <T> T getFeature(Class<T> type) {
        for (Feature feature : features) {
            if (type.isInstance(feature)) return type.cast(feature);
        }
        return null;
    }
}
