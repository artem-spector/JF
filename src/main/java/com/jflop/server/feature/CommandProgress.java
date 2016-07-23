package com.jflop.server.feature;

/**
 * Status of the current command
 *
 * @author artem
 *         Date: 7/23/16
 */
public class CommandProgress {
    public long createdAt;
    public long sentAt;
    public int progressReported;
}
