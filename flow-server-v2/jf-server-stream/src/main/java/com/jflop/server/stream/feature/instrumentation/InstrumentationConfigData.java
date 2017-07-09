package com.jflop.server.stream.feature.instrumentation;

import org.jflop.config.MethodConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class InstrumentationConfigData {

    public Map<String, ClassInstrumentationData> instrumentedClasses;

    public void addInstrumentedClass(String className, Set<MethodConfiguration> methods) {
        getInstrumentedClasses().put(className, new ClassInstrumentationData(className, methods));
    }

    public void blackListClass(String className, String reason) {
        getInstrumentedClasses().put(className, new ClassInstrumentationData(className, reason));
    }

    private Map<String, ClassInstrumentationData> getInstrumentedClasses() {
        if (instrumentedClasses == null) instrumentedClasses = new HashMap<>();
        return instrumentedClasses;
    }
}
