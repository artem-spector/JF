package com.jflop.server.admin;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Admin data access object
 *
 * @author artem
 *         Date: 7/2/16
 */
@Component
public class AdminDAO {

    private Map<String, AccountData> accounts = new HashMap<>();

    public void createAccount(String accountId) {
        accounts.put(accountId, new AccountData());
    }

    public boolean accountExists(String accountId) {
        return accounts.get(accountId) != null;
    }

    public List<JFAgent> getAgents(String accountId) {
        return accounts.get(accountId).getAgents();
    }

    public JFAgent createAgent(String accountId, String name) {
        JFAgent agent = new JFAgent(name);
        accounts.get(accountId).getAgents().add(agent);
        return agent;
    }

    public void updateAgent(String accountId, String agentId, String name) {
        for (JFAgent agent : accounts.get(accountId).getAgents()) {
            if (agent.id.equals(agentId)) {
                agent.name = name;
                return;
            }
        }
        throw new NullPointerException();
    }

    public void deleteAgent(String accountId, String agentId) {
        List<JFAgent> agents = accounts.get(accountId).getAgents();
        for (Iterator<JFAgent> iterator = agents.iterator(); iterator.hasNext();) {
            JFAgent agent = iterator.next();
            if (agent.id.equals(agentId)) {
                iterator.remove();
                return;
            }
        }
        throw new NullPointerException();
    }
}
