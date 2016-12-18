package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jflop.server.persistency.ValuePair;
import com.jflop.server.util.DigestUtil;

import java.security.MessageDigest;
import java.util.*;

/**
 * Unique thread dump, including the thread state and stack trace.
 * The dumpId field is a digest of the account ID and the stack trace, and serves as the document ID
 *
 * @author artem
 *         Date: 11/26/16
 */
public class ThreadMetadata extends Metadata {

    private static Set<String> NOT_INSTRUMENTABLE_PACKAGES = new HashSet<>(Arrays.asList("java.", "org.jflop."));

    public String dumpId;
    public Thread.State threadState;
    public StackTraceElement[] stackTrace;
    public boolean instrumentable;

    public void read(Map<String, Object> json) {
        threadState = Thread.State.valueOf((String) json.get("threadState"));
        List<Map<String, Object>> stackTraceJson = (List<Map<String, Object>>) json.get("stackTrace");
        stackTrace = new StackTraceElement[stackTraceJson.size()];
        for (int i = 0; i < stackTraceJson.size(); i++) {
            Map<String, Object> elementJson = stackTraceJson.get(i);
            stackTrace[i] = new StackTraceElement(
                    (String) elementJson.get("className"),
                    (String) elementJson.get("methodName"),
                    (String) elementJson.get("fileName"),
                    (Integer) elementJson.get("lineNumber"));
        }

        calculateDumpId();
        instrumentable = !getInstrumentableMethods().isEmpty();
    }

    @Override
    public String getDocumentId() {
        return dumpId;
    }

    @JsonIgnore
    public Set<ValuePair<String, String>> getInstrumentableMethods() {
        Set<ValuePair<String, String>> res = new HashSet<>();
        for (StackTraceElement element : stackTrace) {
            boolean instrumentable = !element.isNativeMethod();
            if (instrumentable) {
                String className = element.getClassName();
                for (String prefix : NOT_INSTRUMENTABLE_PACKAGES) {
                    if (className.startsWith(prefix)) {
                        instrumentable = false;
                        break;
                    }
                }

                if (instrumentable) {
                    res.add(new ValuePair<>(className, element.getMethodName()));
                }
            }
        }
        return res;
    }

    private void calculateDumpId() {
        MessageDigest digest = DigestUtil.initDigest(agentJvm);
        DigestUtil.addStringsToDigest(digest, threadState.toString());
        for (StackTraceElement element : stackTrace) {
            String fileName = element.getFileName();
            if (fileName != null) DigestUtil.addStringsToDigest(digest, fileName);
            DigestUtil.addStringsToDigest(digest,
                    String.valueOf(element.getLineNumber()),
                    String.valueOf(element.getClassName()),
                    String.valueOf(element.getMethodName()));
        }
        dumpId = DigestUtil.digestToHexString(digest);
    }

}
