package com.jflop.server.stream.base;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem on 30/05/2017.
 */
public class SlidingWindow<V> {

    public interface Visitor<V> {
        void processDataEntry(Map.Entry<Long, V> value, List<Map.Entry<Long, V>> prevValues, List<Map.Entry<Long, V>> nextValues);
    }

    private NavigableMap<Long, V> data;

    public SlidingWindow(NavigableMap<Long, V> data) {
        this.data = data;
    }

    public void scanValues(int maxPrevValues, int maxNextValues, Visitor<V> visitor) {
        List<Map.Entry<Long, V>> allEntries = new ArrayList<>(data.entrySet());

        for (int i = 0; i < allEntries.size(); i++) {
            int from = Math.max(0, i - maxPrevValues);
            int to = Math.min(allEntries.size(), i + maxNextValues + 1);
            List<Map.Entry<Long, V>> prevEntries = allEntries.subList(from, i);
            List<Map.Entry<Long, V>> nextEntries = allEntries.subList(i + 1, to);

            if (!prevEntries.isEmpty() && prevEntries.size() == nextEntries.size())
                visitor.processDataEntry(allEntries.get(i), prevEntries, nextEntries);
        }
    }

}
