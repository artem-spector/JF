package com.jflop.server.feature;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonProperty
    protected Map<String, Object> command;

    @JsonProperty
    private CommandProgress progress;

    @JsonProperty
    protected String error;

    protected Feature(String name) {
        this.name = name;
    }

    protected void sendCommand(String name, Object data) {
        this.error = null;
        this.command = new HashMap<>();
        this.command.put("name", name);
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

    public Map<String,Object> asJson() {
        Map<String, Object> res = new HashMap<>();
        res.put("name", name);
        res.put("error", error);
        res.put("state", getState());
        if (command != null) {
            Map<String, Object> commandMap = new HashMap<>();
            commandMap.put("name", command.get("name"));
            commandMap.put("createdAt", progress.createdAt);
            commandMap.put("sentAt", progress.sentAt);
            commandMap.put("progressReported", progress.progressReported);
            res.put("command", commandMap);
        }
        return res;
    }

    public String getError() {
        return error;
    }

    protected abstract void processInput(Object input);

    protected abstract Map<String, Object> getState();
}
