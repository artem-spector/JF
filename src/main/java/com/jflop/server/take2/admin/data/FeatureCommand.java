package com.jflop.server.take2.admin.data;

import java.util.Date;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class FeatureCommand {

    public String featureId;
    public String commandName;
    public Object commandParam;
    public Date createdAt;
    public Date sentAt;

    public Date respondedAt;
    public String successText;
    public String errorText;
    public int progressPercent;

    public FeatureCommand() {
    }

    public FeatureCommand(String featureId, String commandName, Object commandParam) {
        this.featureId = featureId;
        this.commandName = commandName;
        this.commandParam = commandParam;
        createdAt = new Date();
    }

}
