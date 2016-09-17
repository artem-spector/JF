package com.jflop.server.take2.feature;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
@Component
public class FeatureManager {

    private Map<String, AgentFeature> allFeatures = new HashMap<>();

    // DI
    public void setFeatures(Collection<AgentFeature> features) {
        for (AgentFeature feature : features) {
            allFeatures.put(feature.featureId, feature);
        }
    }


}
