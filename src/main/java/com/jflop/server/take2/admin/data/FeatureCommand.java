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

    public String commandName;
    public Map<String, Object> commandParam;
    public Date createdAt;
    public Date sentAt;

    public Date respondedAt;
    public String successText;
    public String errorText;
    public int progressPercent;
}
