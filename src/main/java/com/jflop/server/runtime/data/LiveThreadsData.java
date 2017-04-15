package com.jflop.server.runtime.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.List;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 3/4/17
 */
public class LiveThreadsData extends AgentData {

    @JsonIgnore
    public List liveThreads;

    @JsonProperty
    public String getLiveThreadsStr() throws JsonProcessingException {
        return MAPPER.writeValueAsString(liveThreads);
    }

    @JsonProperty
    public void setLiveThreadStr(String str) throws IOException {
        liveThreads = MAPPER.readValue(str, List.class);
    }

    public void init(List liveThreads) {
        this.liveThreads = liveThreads;
    }

}
