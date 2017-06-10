package com.jflop.server.rest.admin;

import com.jflop.server.rest.admin.data.AccountData;
import com.jflop.server.rest.admin.data.JFAgent;
import com.jflop.server.rest.persistency.DocType;
import com.jflop.server.rest.persistency.IndexTemplate;
import com.jflop.server.rest.persistency.PersistentData;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
@Component
public class AccountIndex extends IndexTemplate {

    private static final Logger logger = Logger.getLogger(AccountIndex.class.getName());
    private static final String ACCOUNT_INDEX = "jf-accounts";

    public AccountIndex() {
        super(ACCOUNT_INDEX + "-template", ACCOUNT_INDEX + "*", new DocType("account", "persistency/accountMapping.json", AccountData.class));
    }

    @Override
    public String indexName() {
        return ACCOUNT_INDEX;
    }

    public AccountData createAccount(String accountName) {
        AccountData data = new AccountData(accountName, UUID.randomUUID().toString());
        createDocument(new PersistentData<>(data.accountId, 0, data));
        logger.fine("Created account " + accountName + ": " + data.accountId);
        return data;
    }

    public boolean deleteAccount(String accountId) {
        logger.fine("Delete account " + accountId);
        return deleteDocument(new PersistentData<>(accountId, 0));
    }

    public AccountData getAccount(String accountId) {
        PersistentData<AccountData> document = getDocument(new PersistentData<>(accountId, 0), AccountData.class);
        return document == null ? null : document.source;
    }

    public boolean addAgent(String accountId, JFAgent agent) {
        PersistentData<AccountData> document = getDocument(new PersistentData<>(accountId, 0), AccountData.class);
        AccountData account = document.source;
        account.agents.add(agent);

        PersistentData<Object> res = updateDocument(new PersistentData<>(document.id, document.version, account));
        logger.fine("Account " + accountId + " added agent: " + agent.agentId);
        return res.version > document.version;
    }

    public boolean updateAgent(String accountId, String agentId, String agentName) {
        PersistentData<AccountData> document = getDocument(new PersistentData<>(accountId, 0), AccountData.class);
        if (document == null) throw new RuntimeException("Invalid accountId");
        JFAgent jfAgent = document.source.getAgent(agentId);
        if (jfAgent == null) throw new RuntimeException("Invalid agentId");
        jfAgent.agentName = agentName;

        PersistentData<AccountData> res = updateDocument(document);
        logger.fine("Account " + accountId + " updated agent: " + jfAgent.agentId);
        return res.version > document.version;
    }

    public boolean deleteAgent(String accountId, String agentId) {
        PersistentData<AccountData> document = getDocument(new PersistentData<>(accountId, 0), AccountData.class);
        AccountData account = document.source;
        boolean res = false;
        if (account.agents != null) {
            res = account.removeAgent(agentId);
        }
        updateDocument(new PersistentData<>(document.id, document.version, account));
        logger.fine("Account " + accountId + " deleted agent: " + agentId);
        return res;
    }

    public AccountData findByAgent(String agentId) {
        logger.fine("Looking for account by agent: " + agentId);
        PersistentData<AccountData> doc = findSingle(QueryBuilders.matchQuery("agents.agentId", agentId), AccountData.class);
        return doc == null ? null : doc.source;
    }

    public AccountData findByName(String accountName) {
        logger.fine("Looking for account by name: " + accountName);
        PersistentData<AccountData> doc = findSingle(QueryBuilders.termQuery("accountName", accountName), AccountData.class);
        return doc == null ? null : doc.source;
    }
}
