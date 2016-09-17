package com.jflop.server.take2.admin.data;

import java.util.Date;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class AgentFeatureState {

    public AgentJVM agentJvm;
    public String featureId;
    public Date lastReportedAt;

    public FeatureCommand command;
}
