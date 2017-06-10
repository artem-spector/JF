package com.jflop.server.util;

import org.apache.commons.codec.binary.Hex;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 10/06/2017
 */
public class DigestUtil {

    private static final String SHA_1 = "SHA-1";
    private static final String UTF_8 = "UTF-8";

    public static String uniqueId(String... strings) {
        MessageDigest digest = initDigest();
        if (strings != null) addStringsToDigest(digest, strings);
        return digestToHexString(digest);
    }

    public static MessageDigest initDigest() {
        try {
            return MessageDigest.getInstance(SHA_1);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addStringsToDigest(MessageDigest digest, String... strings) {
        try {
            for (String str : strings) {
                if (str != null)
                    digest.update(str.getBytes(UTF_8));
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String digestToHexString(MessageDigest digest) {
        return Hex.encodeHexString(digest.digest());
    }
}

