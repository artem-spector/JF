package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.persistency.DocType;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates instances of AgentData initialized with common fields
 *
 * @author artem on 12/6/16.
 */
public class AgentDataFactory {

    private Map<Class, String> dataTypes;
    private AgentJVM agentJVM;
    private Date time;

    public AgentDataFactory(AgentJVM agentJVM, Date time, Collection<? extends DocType> docTypes) {
        this.agentJVM = agentJVM;
        this.time = time;
        dataTypes = new HashMap<>();
        for (DocType docType : docTypes) {
            dataTypes.put(docType.type, docType.docType);
        }

    }

    public AgentJVM getAgentJVM() {
        return agentJVM;
    }

    public <T extends AgentData> T createInstance(Class<T> type) {
        try {
            T instance = type.newInstance();
            instance.dataType = dataTypes.get(type);
            if (instance.dataType == null) throw new Exception("Unsupported data type: " + type.getName());

            instance.agentJvm = agentJVM;
            instance.time = time;
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
