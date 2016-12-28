package com.jflop.server.runtime.data;

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

    public void read(Map<String, Object> json) {
        threadState = Thread.State.valueOf((String) json.get("threadState"));
        List<Map<String, Object>> stackTraceJson = (List<Map<String, Object>>) json.get("stackTrace");
        stackTrace = new StackTraceElement[stackTraceJson.size()];
        for (int i = 0; i < stackTraceJson.size(); i++) {
            Map<String, Object> elementJson = stackTraceJson.get(i);
            stackTrace[i] = new StackTraceElement(
                    getValue(elementJson, "className", "UNKNOWN"),
                    getValue(elementJson, "methodName", "UNKNOWN"),
                    getValue(elementJson, "fileName", "UNKNOWN"),
                    getValue(elementJson, "lineNumber", -1));
        }

        calculateDumpId();
    }

    @Override
    public String getDocumentId() {
        return dumpId;
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

    public boolean isInstrumentable(StackTraceElement element) {
        if (element.isNativeMethod()) return false;
        String className = element.getClassName();
        for (String prefix : NOT_INSTRUMENTABLE_PACKAGES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private <T> T getValue(Map<String, Object> map, String key, T defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : (T) value;
    }
}
