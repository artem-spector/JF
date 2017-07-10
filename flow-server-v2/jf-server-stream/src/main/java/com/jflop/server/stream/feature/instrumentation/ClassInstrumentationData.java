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

    public String className;
    public boolean isBlacklisted;
    public String blacklistReason;
    public Map<String, List<String>> methodSignatures;

    public ClassInstrumentationData() {
    }

    public ClassInstrumentationData(String className, Set<MethodConfiguration> methods) {
        this.className = className;
        this.methodSignatures = new HashMap<>();
        for (MethodConfiguration methodConf : methods) {
            methodSignatures.computeIfAbsent(methodConf.methodName, mtd -> new ArrayList<>()).add(methodConf.methodDescriptor);
        }
    }

    public ClassInstrumentationData(String blackListedClass, String reason) {
        this.className = blackListedClass;
        isBlacklisted = true;
        blacklistReason = reason;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof ClassInstrumentationData)) return false;

        ClassInstrumentationData that = (ClassInstrumentationData) obj;
        return Arrays.equals(
                new Object[] {className, isBlacklisted, blacklistReason, methodSignatures},
                new Object[] {that.className, that.isBlacklisted, that.blacklistReason, that.methodSignatures});
    }
}
