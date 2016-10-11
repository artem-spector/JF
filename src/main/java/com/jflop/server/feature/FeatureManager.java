package com.jflop.server.feature;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
@Component
public class FeatureManager {

    private Map<String, AgentFeature> allFeatures = new HashMap<>();
    private String[] defaultFeatures;

    @Autowired
    public void setFeatures(Collection<AgentFeature> features) {
        List<String> defaultFeatures = new ArrayList<>();
        for (AgentFeature feature : features) {
            allFeatures.put(feature.featureId, feature);
            defaultFeatures.add(feature.featureId);
        }
        this.defaultFeatures = defaultFeatures.toArray(new String[defaultFeatures.size()]);
    }

    public AgentFeature getFeature(String featureId) {
        AgentFeature res = allFeatures.get(featureId);
        if (res == null) throw new RuntimeException("Invalid feature ID");
        return res;
    }

    public String[] getDefaultFeatures() {
        return defaultFeatures;
    }
}
