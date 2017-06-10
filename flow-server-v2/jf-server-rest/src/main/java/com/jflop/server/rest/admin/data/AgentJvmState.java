package com.jflop.server.rest.admin.data;

import java.util.Date;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class AgentJvmState {

    public AgentJVM agentJvm;
    public Date lastReportedAt;

    public AgentJvmState() {
    }

    public AgentJvmState(AgentJVM agentJvm) {
        this.agentJvm = agentJvm;
        lastReportedAt = new Date();
    }

}
