package com.jflop.server.stream.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem on 17/05/2017.
 */
public class TimeWindow<T> {

    private final NavigableMap<Long, T> EMPTY = Collections.unmodifiableNavigableMap(new TreeMap<>());

    @JsonProperty
    private long maxSizeMillis;

    @JsonProperty
    private TreeMap<Long, T> window;

    public TimeWindow() {
    }

    public void init(Long maxSizeMillis) {
        this.maxSizeMillis = maxSizeMillis;
        window = new TreeMap<>();
    }

    public T getValue(long timestamp) {
        return window.get(timestamp);
    }

    public void putValue(long timestamp, T value) {
        window.put(timestamp, value);
        while (window.lastKey() - window.firstKey() > maxSizeMillis)
            window.remove(window.firstKey());
    }

    public void putAll(TimeWindow<T> other) {
        if (other.window.isEmpty()) return;
        
        window.putAll(other.window);
        while (window.lastKey() - window.firstKey() > maxSizeMillis)
            window.remove(window.firstKey());
    }

    @JsonIgnore
    public NavigableMap<Long, T> getRecentValues() {
        if (window.isEmpty()) return EMPTY;

        long last = window.lastKey();
        return getValues(last - maxSizeMillis / 2, last);
    }

    @JsonIgnore
    public T getLastValue() {
        return window.isEmpty() ? null : window.lastEntry().getValue();
    }

    public NavigableMap<Long, T> getValues(long from, long to) {
        if (from > to)
            throw new IllegalArgumentException("from is greater than to");
        else if (window.isEmpty())
            return EMPTY;

        from = Math.max(from, window.firstKey());
        if (from > window.lastKey())
            return EMPTY;

        to = Math.min(to, window.lastKey());
        if (to < window.firstKey())
            return EMPTY;

        return Collections.unmodifiableNavigableMap(window.subMap(from, true, to, true));
    }

    @Override
    public String toString() {
        String str = "(";
        for (Map.Entry<Long, T> entry : window.entrySet()) {
            str += "\n\t" + entry.getKey();
            str += ": " + entry.getValue();
        }

        return str + ")";
    }

}
