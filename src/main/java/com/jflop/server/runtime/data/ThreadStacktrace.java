package com.jflop.server.runtime.data;

import java.util.Arrays;

/**
 * Thread state and stacktrace that can be kept in a set or compared to another stack trace
 *
 * @author artem
 *         Date: 11/26/16
 */
public class ThreadStacktrace {

    public final Thread.State threadState;
    public final StackTraceElement[] stackTrace;

    public ThreadStacktrace(Thread.State threadState, StackTraceElement[] stackTrace) {
        this.threadState = threadState;
        this.stackTrace = stackTrace;
    }

    /**
     * Get the number of elements in this stack trace starting from the last one,
     * that represent a common path with given stack trace.
     *
     * @param other another stack trace to compare with
     * @return the number of common elements in the path, 0 means no common path
     */
    public int getCommonPathLength(ThreadStacktrace other) {
        int count = 0;
        while (stackTrace[stackTrace.length - 1 - count].equals(other.stackTrace[other.stackTrace.length - 1 - count])) count++;
        return count;
    }

    @Override
    public int hashCode() {
        int res = threadState.hashCode();
        for (StackTraceElement element : stackTrace) {
            res += element.hashCode();
        }
        return res;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof  ThreadStacktrace)) return false;

        ThreadStacktrace that = (ThreadStacktrace) obj;
        Object[] thisState = new Object[] {threadState, stackTrace};
        Object[] thatState = new Object[] {that.threadState, that.stackTrace};
        return Arrays.deepEquals(thisState, thatState);
    }
}
