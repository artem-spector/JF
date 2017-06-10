package com.jflop.server.stream.feature.load;

import com.jflop.server.stream.base.SlidingWindow;
import com.jflop.server.stream.base.TimeWindow;
import com.jflop.server.stream.ext.AgentStateStore;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NavigableMap;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/25/17
 */
public class LoadDataStore extends AgentStateStore<TimeWindow<LoadData>> {

    private static final Logger logger = LoggerFactory.getLogger(LoadDataStore.class);

    public LoadDataStore() {
        super("LoadDataStore", 60 * 1000, new TypeReference<TimeWindow<LoadData>>() { });
    }

    public void add(RawLoadData rawData) {
        LoadData loadData = new LoadData();
        loadData.rawData = rawData;
        updateWindow(window -> window.putValue(timestamp(), loadData));
    }

    public void processSlidingData(int maxPrevValues, int maxNextValues, SlidingWindow.Visitor<LoadData> visitor) {
        updateWindow(window -> {
            NavigableMap<Long, LoadData> recentValues = window.getRecentValues();
            logger.info("load sliding data: " + recentValues.size());
            new SlidingWindow<>(recentValues).scanValues(maxPrevValues, maxNextValues, visitor);
        });
    }

}
