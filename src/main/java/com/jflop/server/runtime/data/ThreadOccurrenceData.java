package com.jflop.server.runtime.data;

/**
 * Represents the number of occurrences of a specific thread in one thread dump
 *
 * @author artem on 12/6/16.
 */
public class ThreadOccurrenceData extends RawData {

    public String dumpId;
    public int count;
}