package com.jflop.server.take2.runtime;

import com.jflop.server.persistency.PersistentData;
import com.jflop.server.take2.admin.AccountIndex;
import com.jflop.server.take2.admin.AgentJVMIndex;
import com.jflop.server.take2.admin.data.*;
import com.jflop.server.take2.feature.FeatureManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/24/16
 */
@Component
public class RuntimeDAO {

    @Autowired
    private FeatureManager featureManager;

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJVMIndex;

    public Map<String, Object> reportFeaturesData(String agentId, String jvmId, Map<String, Object> featuresData) {
        AccountData account = accountIndex.findByAgent(agentId);
        if (account == null) throw new RuntimeException("Invalid agent ID");

        AgentJVM agentJVM = new AgentJVM(account.accountId, agentId, jvmId);
        PersistentData<AgentJvmState> jvmState = agentJVMIndex.getAgentJvmState(agentJVM, true);
        jvmState.source.lastReportedAt = new Date();

        JFAgent agent = account.getAgent(agentId);
        for (Map.Entry<String, Object> entry : featuresData.entrySet()) {
            String featureId = entry.getKey();
            validateFeature(agent, featureId);

            FeatureCommand command = jvmState.source.getCommand(featureId);
            FeatureCommand update = featureManager.getFeature(featureId).parseCommandUpdate(entry.getValue());
            if (command != null) {
                command.updateFrom(update);
            } else {
                command = new FeatureCommand();
                jvmState.source.setCommand(command);
                command.updateFrom(update);
            }
        }
        agentJVMIndex.updateDocument(jvmState);


        return null;
    }

    private void validateFeature(JFAgent agent, String featureId) {
        for (String enabledFeature : agent.enabledFeatures) {
            if (enabledFeature.equals(featureId)) return;
        }
        throw new RuntimeException("Invalid feature ID");
    }
}
