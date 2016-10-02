package com.jflop.server.take2.admin;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.take2.admin.data.AccountData;
import com.jflop.server.take2.admin.data.JFAgent;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
@Component
public class AccountIndex extends IndexTemplate {

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
        return createDocument(new PersistentData<>(data.accountId, 0, data)).source;
    }

    public boolean deleteAccount(String accountId) {
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
        return res.version > document.version;
    }

    public boolean updateAgent(String accountId, String agentId, String agentName) {
        PersistentData<AccountData> document = getDocument(new PersistentData<>(accountId, 0), AccountData.class);
        if (document == null) throw new RuntimeException("Invalid accountId");
        JFAgent jfAgent = document.source.getAgent(agentId);
        if (jfAgent == null) throw new RuntimeException("Invalid agentId");
        jfAgent.agentName = agentName;

        PersistentData<AccountData> res = updateDocument(document);
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
        return res;
    }

    public AccountData findByAgent(String agentId) {
        PersistentData<AccountData> doc = findSingle(QueryBuilders.matchQuery("agents.agentId", agentId), AccountData.class);
        return doc == null ? null : doc.source;
    }

    public AccountData findByName(String accountName) {
        PersistentData<AccountData> doc = findSingle(QueryBuilders.termQuery("accountName", accountName), AccountData.class);
        return doc == null ? null : doc.source;
    }
}
