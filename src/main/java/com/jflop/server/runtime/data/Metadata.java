package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.tomcat.util.buf.HexUtils;
import org.elasticsearch.common.hash.MessageDigests;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

/**
 * Base class for "metadata" objects, which define their own unique IDs
 *
 * @author artem on 12/7/16.
 */
public abstract class Metadata extends AgentData {

    protected MessageDigest initDigest() {
        MessageDigest digest = MessageDigests.sha1();
        addStringToDigest(agentJvm.accountId, digest);
        addStringToDigest(agentJvm.agentId, digest);
        addStringToDigest(agentJvm.jvmId, digest);
        return digest;
    }

    protected void addStringToDigest(String str, MessageDigest digest) {
        try {
            digest.update(str.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected String digestToString(MessageDigest digest) {
        byte[] res = digest.digest();
        return HexUtils.toHexString(res);
    }

    /**
     * Get the custom document ID.
     * @return document ID, not null
     */
    @JsonIgnore
    public abstract String getDocumentId();
}
