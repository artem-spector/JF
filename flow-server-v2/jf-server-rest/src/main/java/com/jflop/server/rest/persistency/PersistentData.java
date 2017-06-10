package com.jflop.server.rest.persistency;

/**
 * Source, id, and version
 *
 * @author artem
 *         Date: 8/20/16
 */
public class PersistentData<T> {

    public String id;
    public long version;
    public T source;

    public PersistentData(String id, long version, T source) {
        this.id = id;
        this.version = version;
        this.source = source;
    }

    public PersistentData(T source) {
        this(null, 0, source);
    }

    public PersistentData(String id, long version) {
        this(id, version, null);
    }
}
