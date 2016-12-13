package com.jflop;

/**
 * TODO: Document!
 *
 * @author artem on 13/12/2016.
 */
public class TestUtil {

    private static long begin;

    public static synchronized void reset() {
        begin = 0;
    }

    public static String prefix() {
        return String.valueOf(System.currentTimeMillis() - getBegin()) + ": " + Thread.currentThread().getName() + " -> ";
    }

    private static synchronized long getBegin() {
        if (begin == 0) begin = System.currentTimeMillis();
        return begin;
    }
}
