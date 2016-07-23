package com.jflop.server.feature;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent server side feature contract
 *
 * @author artem
 *         Date: 7/23/16
 */
public abstract class Feature {

    public final String name;

    protected Map<String, Object> command;
    private CommandProgress progress;
    protected String error;

    protected Feature(String name) {
        this.name = name;
    }

    protected void sendCommand(String name, Object data) {
        this.error = null;
        this.command = new HashMap<>();
        this.command.put(name, data);
        this.progress = new CommandProgress();
        progress.createdAt = System.currentTimeMillis();
    }

    protected void setCommandProgress(int progress) {
        this.progress.progressReported = progress;
    }

    protected void commandDone() {
        command = null;
        progress = null;
    }

    public Map<String, Object> poll(Object input) {
        if (input != null) {
            if (input instanceof Map && ((Map) input).containsKey("error")) {
                commandDone();
                error = (String) ((Map) input).get("error");
            } else
                processInput(input);
        }
        if (command != null && progress.sentAt == 0) {
            progress.sentAt = System.currentTimeMillis();
            return command;
        }
        return null;
    }

    public CommandProgress getProgress() {
        return progress;
    }

    public String getError() {
        return error;
    }

    protected abstract void processInput(Object input);
}
