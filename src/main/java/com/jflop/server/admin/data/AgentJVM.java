package com.jflop.server.admin.data;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class AgentJVM {

    public String accountId;
    public String agentId;
    public String jvmId;

    public AgentJVM() {
    }

    public AgentJVM(String accountId, String agentId, String jvmId) {
        this.accountId = accountId;
        this.agentId = agentId;
        this.jvmId = jvmId;
    }
}
