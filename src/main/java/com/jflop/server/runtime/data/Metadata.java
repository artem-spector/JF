package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jflop.server.util.DigestUtil;
import org.apache.tomcat.util.buf.HexUtils;
import org.elasticsearch.common.hash.MessageDigests;
import org.springframework.util.DigestUtils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

/**
 * Base class for "metadata" objects, which define their own unique IDs
 *
 * @author artem on 12/7/16.
 */
public abstract class Metadata extends AgentData {

    /**
     * Get the custom document ID.
     * @return document ID, not null
     */
    @JsonIgnore
    public abstract String getDocumentId();

    @JsonIgnore
    public boolean mergeTo(Metadata existing) {
        return false;
    }
}
