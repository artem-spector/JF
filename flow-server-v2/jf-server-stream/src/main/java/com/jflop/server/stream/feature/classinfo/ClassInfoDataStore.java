package com.jflop.server.stream.feature.classinfo;

import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jflop.config.MethodConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 03/06/2017
 */
public class ClassInfoDataStore extends AgentStateStore<TimeWindow<ClassInfoData>> {

    private static final Logger logger = LoggerFactory.getLogger(ClassInfoDataStore.class);

    public ClassInfoDataStore() {
        super("ClassInfoDataStore", 2 * 60 * 60 * 1000, new TypeReference<TimeWindow<ClassInfoData>>() {
        });
    }

    public void add(ClassInfoData data) {
        updateWindow(window -> window.putValue(timestamp(), data));
    }

    /**
     * Find the method signatures for given class and method names.
     * If not all the classes or methods can be found, return null.
     *
     * @param classMethods class name to set of method names map
     * @return method configurations for requested classes/methods, or null if not all of them were found.
     */
    public Set<MethodConfiguration> findMethodSignatures(Map<String, Set<String>> classMethods) {
        Set<MethodConfiguration> res = new HashSet<>();
        Map<String, Map<String, Set<String>>> allClassInfo = getAllClassInfo();

        for (Map.Entry<String, Set<String>> entry : classMethods.entrySet()) {
            String className = entry.getKey();
            Map<String, Set<String>> allMethodSignatures = allClassInfo.get(className);
            if (allMethodSignatures == null) return null;

            Set<String> targetSignatures = new HashSet<>();
            for (String methodName : entry.getValue()) {
                Set<String> signatures = allMethodSignatures.get(methodName);
                if (signatures == null) return null;
                for (String signature : signatures) {
                    res.add(new MethodConfiguration(className + "." + signature));
                }
            }
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
