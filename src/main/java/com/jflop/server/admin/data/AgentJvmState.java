package com.jflop.server.admin.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
public class AgentJvmState {

    public AgentJVM agentJvm;
    public Date lastReportedAt;
    public List<String> errors;

    public List<FeatureCommand> featureCommands;

    public AgentJvmState() {
    }

    public AgentJvmState(AgentJVM agentJvm) {
        this.agentJvm = agentJvm;
        lastReportedAt = new Date();
        featureCommands = new ArrayList<>();
    }

    public FeatureCommand getCommand(String featureId) {
        for (FeatureCommand command : featureCommands) {
            if (command.featureId.equals(featureId)) return command;
        }
        return null;
    }

    public void setCommand(FeatureCommand command) {
        FeatureCommand found = getCommand(command.featureId);
        if (found != null)
            featureCommands.remove(found);
        featureCommands.add(command);
    }
}
