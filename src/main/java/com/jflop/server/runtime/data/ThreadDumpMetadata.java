package com.jflop.server.runtime.data;

import org.apache.tomcat.util.buf.HexUtils;
import org.elasticsearch.common.hash.MessageDigests;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Date;

/**
 * Information about a flow
 *
 * @author artem
 *         Date: 11/26/16
 */
public class ThreadDumpMetadata {

    public String dumpId;
    public String accountId;
    public Date createdAt;
    public Thread.State threadState;
    public StackTraceElement[] stackTrace;

    public ThreadDumpMetadata() {
    }

    public ThreadDumpMetadata(String accountId, Thread.State threadState, StackTraceElement[] stackTrace) {
        this.createdAt = new Date();
        this.accountId = accountId;
        this.threadState = threadState;
        this.stackTrace = stackTrace;
        dumpId = getDigest(accountId, threadState, stackTrace);
    }

    /**
     * Produce string representation of digest including account ID and the thread dump
     * @return string containing base-64 representation of the digest
     */
    private String getDigest(String accountId, Thread.State threadState, StackTraceElement[] stackTrace) {
        MessageDigest digest = MessageDigests.sha1();
        try {
            digest.update(accountId.getBytes("UTF-8"));
            digest.update(threadState.toString().getBytes("UTF-8"));
            for (StackTraceElement element : stackTrace) {
                digest.update(element.getFileName().getBytes("UTF-8"));
                digest.update(String.valueOf(element.getLineNumber()).getBytes("UTF-8"));
                digest.update(String.valueOf(element.getClassName()).getBytes("UTF-8"));
                digest.update(String.valueOf(element.getMethodName()).getBytes("UTF-8"));
            }
            byte[] res = digest.digest();
            return HexUtils.toHexString(res);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
