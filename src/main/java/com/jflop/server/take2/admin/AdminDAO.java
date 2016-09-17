package com.jflop.server.take2.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.take2.admin.data.AccountData;
import com.jflop.server.take2.admin.data.AgentFeatureState;
import com.jflop.server.take2.admin.data.JFAgent;
import com.jflop.server.take2.feature.InstrumentationConfigurationFeature;
import com.sun.xml.internal.xsom.impl.scd.Iterators;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
@Component
public class AdminDAO {

    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AccountIndex accountIndex;

    @Autowired
    private AgentJVMIndex agentJvmIndex;

    public AccountData createAccount(String accountName) {
        return accountIndex.createAccount(accountName);
    }

    public void deleteAccount(String accountId) {
        accountIndex.deleteAccount(accountId);
        agentJvmIndex.deleteAccount(accountId);
    }

    public AccountData getAccount(String accountId) {
        return accountIndex.getAccount(accountId);
    }

    public JFAgent createAgent(String accountId, String agentName) {
        JFAgent agent = new JFAgent();
        agent.agentId = UUID.randomUUID().toString();
        agent.agentName = agentName;
        agent.enabledFeatures = new String[] {InstrumentationConfigurationFeature.FEATURE_ID};

        accountIndex.addAgent(accountId, agent);
        return agent;
    }

    public void deleteAgent(String accountId, String agentId) {
        accountIndex.deleteAgent(accountId, agentId);
        agentJvmIndex.deleteAgent(accountId, agentId);
    }

    public Map<String, Object> getAccountAgentsJson(String accountId) {
        try {
            AccountData account = accountIndex.getAccount(accountId);
            byte[] bytes = mapper.writeValueAsBytes(account);
            Map<String, Object> json = mapper.readValue(bytes, Map.class);

            for (AgentFeatureState featureState : agentJvmIndex.getAgentFeatures(accountId)) {
                Map<String, Object> agentJson = (Map<String, Object>) json.get(featureState.agentJvm.agentId);
                Map<String, Object>  jvms = (Map<String, Object>) agentJson.get("jvms");
                if (jvms == null) {
                    jvms = new HashMap<>();
                    agentJson.put("jvms", jvms);
                }
                bytes = mapper.writeValueAsBytes(featureState.command);
                jvms.put(featureState.agentJvm.jvmId, mapper.readValue(bytes, Map.class));
            }


            return json;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
