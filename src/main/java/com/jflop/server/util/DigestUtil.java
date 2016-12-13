package com.jflop.server.util;

import com.jflop.server.admin.data.AgentJVM;
import org.apache.tomcat.util.buf.HexUtils;
import org.elasticsearch.common.hash.MessageDigests;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

/**
 * TODO: Document!
 *
 * @author artem on 13/12/2016.
 */
public class DigestUtil {

    public static String uniqueId(AgentJVM agentJvm, String... strings) {
        MessageDigest digest = initDigest(agentJvm);
        if (strings != null) addStringsToDigest(digest, strings);
        return digestToHexString(digest);
    }

    public static MessageDigest initDigest(AgentJVM agentJvm) {
        MessageDigest digest = MessageDigests.sha1();
        addStringsToDigest(digest, agentJvm.accountId, agentJvm.agentId, agentJvm.jvmId);
        return digest;
    }

    public static void addStringsToDigest(MessageDigest digest, String... strings) {
        try {
            for (String str : strings) {
                if (str != null)
                    digest.update(str.getBytes("UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String digestToHexString(MessageDigest digest) {
        byte[] res = digest.digest();
        return HexUtils.toHexString(res);
    }

}
