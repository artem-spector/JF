package com.jflop.server.stream.feature.instrumentation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jflop.config.MethodConfiguration;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class InstrumentationConfigData {

    public Map<String, ClassInstrumentationData> instrumentedClasses;

    public void addMethodConfiguration(MethodConfiguration mtd) {
        getInstrumentedClasses().computeIfAbsent(mtd.internalClassName, className -> new ClassInstrumentationData())
                .addMethodConfiguration(mtd);
    }

    public void blackListClass(String className, String reason) {
        getInstrumentedClasses().put(className, new ClassInstrumentationData(className, reason));
    }

    @JsonIgnore
    public Set<MethodConfiguration> getMethodConfigurations() {
        Set<MethodConfiguration> res = new HashSet<>();

        for (Map.Entry<String, ClassInstrumentationData> internalNameEntry : getInstrumentedClasses().entrySet()) {
            ClassInstrumentationData instrumentationData = internalNameEntry.getValue();
            if (instrumentationData.isBlacklisted) continue;

            String internalClassName = internalNameEntry.getKey();
            for (Map.Entry<String, List<String>> entry : instrumentationData.methodSignatures.entrySet()) {
                String methodName = entry.getKey();
                for (String descriptor : entry.getValue()) {
                    res.add(new MethodConfiguration(internalClassName, methodName, descriptor));
                }
            }
        }

        return res;
    }

    public Set<String> getBlacklistedExternalClassNames() {
        Set<String> res = new HashSet<>();
        for (ClassInstrumentationData instrumentationData : getInstrumentedClasses().values()) {
            if (instrumentationData.isBlacklisted)
                res.add(instrumentationData.externalClassName);
        }
        return res;
    }

    @JsonIgnore
    private Map<String, ClassInstrumentationData> getInstrumentedClasses() {
        if (instrumentedClasses == null) instrumentedClasses = new HashMap<>();
        return instrumentedClasses;
    }
}
