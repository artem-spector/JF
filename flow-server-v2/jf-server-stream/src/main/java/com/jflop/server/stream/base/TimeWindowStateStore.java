package com.jflop.server.stream.base;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jflop.server.data.JacksonSerdes;
import org.apache.kafka.streams.processor.StateStoreSupplier;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Key/value state store, where the value is a time window
 *
 * @author artem
 *         Date: 5/21/17
 */
public abstract class TimeWindowStateStore<K, V extends TimeWindow> {

    private static final Logger logger = LoggerFactory.getLogger(TimeWindowStateStore.class);

    public final String storeId;
    private long maxSizeMillis;

    private final TypeReference<K> keyType;
    private final TypeReference<V> windowType;
    private final Class<V> windowClass;

    protected StatefulProcessor container;
    private KeyValueStore<K, V> store;

    protected TimeWindowStateStore(String storeId, long maxSizeMillis, TypeReference<K> keyType, TypeReference<V> windowType) {
        this.storeId = storeId;
        this.maxSizeMillis = maxSizeMillis;
        this.keyType = keyType;
        this.windowType = windowType;
        try {
            String className = windowType.getType().getTypeName();
            className = className.substring(0, className.indexOf("<"));
            windowClass = (Class<V>) Class.forName(className);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate store " + storeId, e);
        }
    }

    StateStoreSupplier createStoreSupplier() {
        return Stores.create(storeId)
                .withKeys(JacksonSerdes.jacksonSerde(keyType))
                .withValues(JacksonSerdes.jacksonSerde(windowType))
                .inMemory()
                .build();
    }

    void init(StatefulProcessor container) {
        this.container = container;
        store = (KeyValueStore<K, V>) container.context.getStateStore(storeId);
    }

    protected V getWindow(K key) {
        V window = store.get(key);
        logger.debug(storeId + " getWindow(" + key + "): " + window);
        if (window == null) {
            try {
                window = windowClass.newInstance();
                window.init(maxSizeMillis);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize store " + storeId, e);
            }
        }
        return window;
    }

    protected void putWindow(K key, V value) {
        logger.debug(storeId + " putWindow(" + key + ", " + value + ")");
        store.put(key, value);
        store.flush();
    }

    protected long timestamp() {
        return container.context.timestamp();
    }

    public void clear() {
        logger.info("Clear state store " + storeId);
        Set<K> allKeys = new HashSet<>();
        KeyValueIterator<K, V> iter = store.all();
        while (iter.hasNext()) {
            allKeys.add(iter.next().key);
        }

        for (K key : allKeys) {
            store.delete(key);
        }
        store.flush();
    }
}
