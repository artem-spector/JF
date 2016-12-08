package com.jflop.server.background;

import com.jflop.server.admin.data.AgentJVM;

import java.util.Date;

/**
 * Lock data for a specific backgound task
 *
 * @author artem on 12/7/16.
 */
public class TaskLockData {

    public String taskName;
    public AgentJVM agentJvm;
    public String lockId;

    public Date lockedUntil;

    public TaskLockData() {
    }

    public TaskLockData(String taskName, AgentJVM agentJvm) {
        this.taskName = taskName;
        this.agentJvm = agentJvm;
        lockId = taskName;
        if (agentJvm != null) {
            if (agentJvm.accountId != null) lockId += "-" + agentJvm.accountId;
            if (agentJvm.agentId != null) lockId += "-" + agentJvm.agentId;
            if (agentJvm.jvmId != null) lockId += "-" + agentJvm.jvmId;
        }
        lockedUntil = new Date(0);
    }
}
