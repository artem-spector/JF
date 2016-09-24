package com.jflop.server.take2.admin.data;

import java.util.Date;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class FeatureCommand {

    public String featureId;
    public String commandName;
    public Map<String, Object> commandParam;
    public Date createdAt;
    public Date sentAt;

    public Date respondedAt;
    public String successText;
    public String errorText;
    public int progressPercent;

    public FeatureCommand() {
    }

    public FeatureCommand(String featureId, String commandName, Map<String, Object> commandParam) {
        this.featureId = featureId;
        this.commandName = commandName;
        this.commandParam = commandParam;
        createdAt = new Date();
    }

    public void updateFrom(FeatureCommand from) {
        if (!commandName.equals(from.commandName)) throw new RuntimeException("Invalid command update");
        this.respondedAt = from.respondedAt;
        this.successText = from.successText;
        this.errorText = from.errorText;
        this.progressPercent = from.progressPercent;
    }
}
