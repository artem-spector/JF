package com.jflop.server.runtime.data;

import com.jflop.server.admin.data.AgentJVM;
import com.jflop.server.runtime.RawDataIndex;

import java.util.Date;

/**
 * Creates instances of RawData initialized with common fields
 *
 * @author artem on 12/6/16.
 */
public class RawDataFactory {

    private RawDataIndex index;
    private AgentJVM agentJVM;
    private Date time;

    public RawDataFactory(RawDataIndex index, AgentJVM agentJVM, Date time) {
        this.index = index;
        this.agentJVM = agentJVM;
        this.time = time;
    }

    public <T extends RawData> T createInstance(Class<T> type) {
        try {
            T instance = type.newInstance();
            instance.dataType = index.getDocType(type);
            instance.agentJvm = agentJVM;
            instance.time = time;
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
