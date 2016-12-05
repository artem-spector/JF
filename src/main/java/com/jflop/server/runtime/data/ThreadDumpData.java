package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;
import org.apache.tomcat.util.buf.HexUtils;
import org.elasticsearch.common.hash.MessageDigests;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Unique thread dump, including the thread state and stack trace.
 * The dumpId field is a digest of the account ID and the stack trace, and serves as the document ID
 *
 * @author artem
 *         Date: 11/26/16
 */
public class ThreadDumpData extends RawData {

    public static final String TYPE = "thread";

    public String dumpId;
    public Thread.State threadState;
    public StackTraceElement[] stackTrace;

    public ThreadDumpData() {
    }

    public ThreadDumpData(Map<String, Object> json) {
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
    }

    @Override
    public String getDocumentId() {
        return dumpId;
    }

    @Override
    public void complete(AgentJVM agentJvm, Date time) {
        super.complete(agentJvm, time);

        MessageDigest digest = MessageDigests.sha1();
        try {
            digest.update(this.agentJvm.accountId.getBytes("UTF-8"));
            digest.update(threadState.toString().getBytes("UTF-8"));
            for (StackTraceElement element : stackTrace) {
                String fileName = element.getFileName();
                if (fileName != null) digest.update(fileName.getBytes("UTF-8"));
                digest.update(String.valueOf(element.getLineNumber()).getBytes("UTF-8"));
                digest.update(String.valueOf(element.getClassName()).getBytes("UTF-8"));
                digest.update(String.valueOf(element.getMethodName()).getBytes("UTF-8"));
            }
            byte[] res = digest.digest();
            dumpId = HexUtils.toHexString(res);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
