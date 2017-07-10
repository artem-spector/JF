package com.jflop.server.stream.feature.instrumentation;

import org.jflop.config.MethodConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
                .methodSignatures.computeIfAbsent(mtd.methodName, methodName -> new ArrayList<>()).add(mtd.methodDescriptor);
    }

    public void blackListClass(String className, String reason) {
        getInstrumentedClasses().put(className, new ClassInstrumentationData(className, reason));
    }

    private Map<String, ClassInstrumentationData> getInstrumentedClasses() {
        if (instrumentedClasses == null) instrumentedClasses = new HashMap<>();
        return instrumentedClasses;
    }

    public boolean covers(InstrumentationConfigData other) {
        if (!instrumentedClasses.keySet().containsAll(other.instrumentedClasses.keySet()))
            return false;

        for (Map.Entry<String, ClassInstrumentationData> entry : other.instrumentedClasses.entrySet()) {
            ClassInstrumentationData otherInstr = entry.getValue();
            ClassInstrumentationData thisInstr = instrumentedClasses.get(entry.getKey());
            if (!otherInstr.equals(thisInstr))
                return false;
        }
        return true;
    }
}
