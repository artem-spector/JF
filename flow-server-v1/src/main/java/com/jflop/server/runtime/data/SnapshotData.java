package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jflop.snapshot.Snapshot;

import java.io.IOException;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem on 27/02/2017.
 */
public class SnapshotData extends AgentData {

    @JsonIgnore
    public Map<String, Object> snapshotJson;

    @JsonProperty
    public String getSnapshotJsonStr() throws JsonProcessingException {
        return MAPPER.writeValueAsString(snapshotJson);
    }

    @JsonProperty
    public void setSnapshotJsonStr(String str) throws IOException {
        snapshotJson = MAPPER.readValue(str, Map.class);
    }

    public void init(Snapshot snapshot) {
        snapshotJson = snapshot.asJson();
    }
}
