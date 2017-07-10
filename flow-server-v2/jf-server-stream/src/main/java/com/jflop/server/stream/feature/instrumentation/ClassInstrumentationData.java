package com.jflop.server.stream.feature.instrumentation;

import org.jflop.config.MethodConfiguration;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 09/07/2017
 */
public class ClassInstrumentationData {

    public String externalClassName;
    public boolean isBlacklisted;
    public String blacklistReason;
    public Map<String, List<String>> methodSignatures;

    public ClassInstrumentationData() {
    }

    public ClassInstrumentationData(String blackListedExternalClassName, String reason) {
        this.externalClassName = blackListedExternalClassName;
        isBlacklisted = true;
        blacklistReason = reason;
    }

    public void addMethodConfiguration(MethodConfiguration mtd) {
        externalClassName = mtd.getExternalClassName();
        if (methodSignatures == null) methodSignatures = new HashMap<>();
        methodSignatures.computeIfAbsent(mtd.methodName, m -> new ArrayList<>()).add(mtd.methodDescriptor);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof ClassInstrumentationData)) return false;

        ClassInstrumentationData that = (ClassInstrumentationData) obj;
        return Arrays.equals(
                new Object[] {externalClassName, isBlacklisted, blacklistReason, methodSignatures},
                new Object[] {that.externalClassName, that.isBlacklisted, that.blacklistReason, that.methodSignatures});
    }
}
