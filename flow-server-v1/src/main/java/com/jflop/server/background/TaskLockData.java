package com.jflop.server.background;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.admin.data.AgentJVM;

import java.io.IOException;
import java.util.Date;

/**
 * Lock data for a specific background task
 *
 * @author artem on 12/7/16.
 */
public class TaskLockData {

    private static final ObjectMapper mapper = new ObjectMapper();

    public String taskName;
    public AgentJVM agentJvm;
    public String lockId;

    public Date lockedUntil;

    @JsonProperty
    private String customStateJson;

    public TaskLockData() {
    }

    public TaskLockData(String taskName, AgentJVM agentJvm) {
        this.taskName = taskName;
        this.agentJvm = agentJvm;
        lockId = taskName;
        if (agentJvm != null) {
            if (agentJvm.accountId != null) lockId += "-" + agentJvm.accountId;
            if (agentJvm.agentId != null) lockId += "-" + agentJvm.agentId;
            if (agentJvm.jvmId != null) lockId += "-" + agentJvm.jvmId;
        }
        lockedUntil = new Date(0); // unlocked
    }

    @JsonIgnore
    public <T> T getCustomState(Class<T> type) {
        if (customStateJson == null || type == String.class)
            return type.cast(customStateJson);

        try {
            return mapper.readValue(customStateJson, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read custom task state.", e);
        }
    }

    @JsonIgnore
    public void setCustomState(Object state) {
        try {
            customStateJson = mapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to write custom task state.", e);
        }
    }
}
