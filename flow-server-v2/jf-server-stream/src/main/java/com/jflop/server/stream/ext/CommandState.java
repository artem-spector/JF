package com.jflop.server.stream.ext;

/**
 * TODO: Document!
 *
 * @author artem on 22/05/2017.
 */
public class CommandState {

    public String command;
    public long sentAt;
    public long respondedAt;
    public int progress;
    public String error;

    public boolean inProgress() {
        return (sentAt > 0) && (error == null) && (respondedAt == 0 || progress < 100);
    }

    @Override
    public String toString() {
        return "(command: '" + command + "', sentAt: '" + sentAt +"', respondedAt: '" + respondedAt + "', progress: '" + progress + "', error: '" + error + "')";
    }
}
