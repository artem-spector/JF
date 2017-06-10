package com.jflop.server.persistency;

import java.util.Arrays;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 8/13/16
 */
public class SomePojo {

    public String name;
    public String description;

    public SomePojo() {
    }

    public SomePojo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof SomePojo)) return false;

        SomePojo that = (SomePojo) obj;
        Object[] thisState = {name, description};
        Object[] thatState = {that.name, that.description};
        return Arrays.equals(thisState, thatState);
    }
}
