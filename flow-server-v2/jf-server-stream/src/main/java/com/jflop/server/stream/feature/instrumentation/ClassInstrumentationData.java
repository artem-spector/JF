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
}
