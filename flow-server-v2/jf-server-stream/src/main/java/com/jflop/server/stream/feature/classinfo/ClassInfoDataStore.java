package com.jflop.server.stream.feature.classinfo;

import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ClassInfoDataStore extends AgentStateStore<TimeWindow<ClassInfoData>> {

    public ClassInfoDataStore() {
        super("ClassInfoDataStore", 2 * 60 * 60 * 1000, new TypeReference<TimeWindow<ClassInfoData>>() {
        });
    }

    public void add(ClassInfoData data) {
        updateWindow(window -> window.putValue(timestamp(), data));
    }

    public Map<String, Set<String>> findMethodSignatures(Map<String, Set<String>> classMethods) {
        Map<String, Set<String>> res = new HashMap<>();
        Map<String, Map<String, Set<String>>> allClassInfo = getAllClassInfo();

        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Map<String, Set<String>> allMethodSignatures = allClassInfo.get(className);
            Set<String> targetSignatures = new HashSet<>();
            for (String methodName : entry.getValue()) {
                targetSignatures.addAll(allMethodSignatures.get(methodName));
            }
            res.put(className, targetSignatures);
        }

        return res;
    }

    public Map<String, Set<String>> findUnknownMethods(Map<String, Set<String>> classMethods) {
        Map<String, Set<String>> res = new HashMap<>();
        Map<String, Map<String, Set<String>>> known = getAllClassInfo();

        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Map<String, Set<String>> knownMethods = known.get(className);
            for (String mtd : entry.getValue()) {
                if (knownMethods == null || knownMethods.get(mtd) == null)
                    res.computeIfAbsent(className, c -> new HashSet<>()).add(mtd);
            }
        }

        return res;
    }

    private Map<String, Map<String, Set<String>>> getAllClassInfo() {
        Map<String, Map<String, Set<String>>> res = new HashMap<>();
        for (ClassInfoData classInfoData : getWindow(agentJVM()).getValues(0, timestamp()).values()) {
            for (Map.Entry<String, Map<String, List<String>>> classEntry : classInfoData.classMethodSignatures.entrySet()) {
                Map<String, Set<String>> methodSignatures = res.computeIfAbsent(classEntry.getKey(), k -> new HashMap<>());
                for (Map.Entry<String, List<String>> methodEntry : classEntry.getValue().entrySet()) {
                    methodSignatures.computeIfAbsent(methodEntry.getKey(), m -> new HashSet<>()).addAll(methodEntry.getValue());
                }
            }
        }
        return res;
    }
}
