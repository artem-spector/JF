package com.jflop.server.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.admin.AdminDAO;
import com.jflop.server.admin.ValidationException;
import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.admin.data.FeatureCommand;
import com.jflop.server.runtime.data.AgentData;
import com.jflop.server.runtime.data.AgentDataFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/11/16
 */
public abstract class AgentFeature {

    public final String featureId;

    protected ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AdminDAO adminDAO;

    protected AgentFeature(String featureId) {
        this.featureId = featureId;
    }

    public abstract FeatureCommand parseCommand(AgentJVM agentJVM, String command, String paramStr) throws ValidationException;

    public abstract List<AgentData> parseReportedData(Object dataJson, FeatureCommand command, AgentDataFactory agentDataFactory);

    protected FeatureCommand getCurrentCommand(AgentJVM agentJvm) {
        return adminDAO.getCurrentCommand(agentJvm, featureId);
    }

    /**
     * If there's no current in progress command for this feature - set the given command as current
     *
     * @param agentJvm     agent JVM
     * @param commandName  command name
     * @param commandParam command parameters
     */
    protected void sendCommandIfNotInProgress(AgentJVM agentJvm, String commandName, Object commandParam) {
        // if there already is a command in progress - return
        FeatureCommand currentCommand = getCurrentCommand(agentJvm);
        if (currentCommand != null && currentCommand.respondedAt == null)
            return;

        // create and set command
        FeatureCommand command = new FeatureCommand(featureId, commandName, commandParam);
        adminDAO.setCommand(agentJvm, command);
    }

}
