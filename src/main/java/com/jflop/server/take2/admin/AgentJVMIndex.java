package com.jflop.server.take2.admin;

import com.jflop.server.persistency.DocType;
import com.jflop.server.persistency.IndexTemplate;
import com.jflop.server.persistency.PersistentData;
import com.jflop.server.take2.admin.data.AgentFeatureState;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 9/17/16
 */
@Component
public class AgentJVMIndex extends IndexTemplate {

    private static final String AGENT_JVM_INDEX = "jf-agent-jvm";
    private static final int MAX_JVM_NUM = 500;
    public static final int MAX_FEATURES = 10;

    public AgentJVMIndex() {
        super(AGENT_JVM_INDEX + "-template", AGENT_JVM_INDEX + "*", new DocType("agent", "agentFeatureState.json", AgentFeatureState.class));
    }

    @Override
    public String indexName() {
        return AGENT_JVM_INDEX;
    }

    public void deleteAccount(String accountId) {
        deleteByQuery(QueryBuilders.termQuery("accountId", accountId));
    }

    public void deleteAgent(String accountId, String agentId) {
        deleteByQuery(
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("accountId", accountId))
                        .must(QueryBuilders.termQuery("agentId", agentId)));
    }

    public List<AgentFeatureState> getAgentFeatures(String accountId) {
        List<PersistentData<AgentFeatureState>> list = find(QueryBuilders.termQuery("accountId", accountId), MAX_JVM_NUM * MAX_FEATURES, AgentFeatureState.class);
        List<AgentFeatureState> res = new ArrayList<>(list.size());
        for (PersistentData<AgentFeatureState> data : list) {
            res.add(data.source);
        }
        return res;
    }
}
