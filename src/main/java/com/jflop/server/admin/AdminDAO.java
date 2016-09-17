package com.jflop.server.admin;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Admin data access object
 *
 * @author artem
 *         Date: 7/2/16
 */
@Component
public class AdminDAO {

    private Map<String, AccountData> accounts = new HashMap<>();
    private Map<String, JFAgent> agents = new HashMap<>();

        public void createAccount(String accountId) {
            accounts.put(accountId, new AccountData());
        }

        public boolean accountExists(String accountId) {
            return accounts.get(accountId) != null;
        }

    public List<JFAgent> getAgents(String accountId) {
        List<String> agentIDs = accounts.get(accountId).getAgentIDs();
        List<JFAgent> res = new ArrayList<>();
        for (String id : agentIDs) {
            res.add(agents.get(id));
        }

        return res;
    }

    public JFAgent createAgent(String accountId, String name) {
        JFAgent agent = new JFAgent(accountId, name);
        accounts.get(accountId).getAgentIDs().add(agent.agentId);
        agents.put(agent.agentId, agent);
        return agent;
    }

    public void updateAgent(String accountId, String agentId, String name) {
        JFAgent agent = agents.get(agentId);
        if (!agent.accountId.equals(accountId)) throw new NullPointerException();
        agent.name = name;
    }

    public void deleteAgent(String accountId, String agentId) {
        JFAgent agent = agents.get(agentId);
        if (!agent.accountId.equals(accountId)) throw new NullPointerException();

        accounts.get(accountId).getAgentIDs().remove(agentId);
        agents.remove(agentId);
    }

    public JFAgent getAgent(String agentId) {
        return agents.get(agentId);
    }
}
