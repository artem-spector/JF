package com.jflop.server.take2.feature;

import java.util.Date;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
public abstract class FeatureCommand {

    public String commandName;
    public Date createdAt;
    public Date sentAt;
    public Date respondedAt;
    public int progress;
    public String successText;
    public String errorText;
}
