package com.jflop.server.stream.feature.threads;

import com.jflop.server.util.DigestUtil;

import java.security.MessageDigest;
import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 31/05/2017
 */
public class ThreadMetadata {

    public String threadId;
    public Thread.State threadState;
    public List<MethodCall> stackTrace;

    public ThreadMetadata() {
    }

    public ThreadMetadata(Thread.State threadState, List<MethodCall> stackTrace) {
        this.threadState = threadState;
        this.stackTrace = stackTrace;

        MessageDigest digest = DigestUtil.initDigest();
        for (MethodCall call : stackTrace) {
            DigestUtil.addStringsToDigest(digest, call.declaringClass, call.methodName, call.fileName, String.valueOf(call.lineNumber));
        }
        threadId = DigestUtil.digestToHexString(digest);
    }

    @Override
    public int hashCode() {
        return threadId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof ThreadMetadata)) return false;
        return threadId.equals(((ThreadMetadata)obj).threadId);
    }
}
