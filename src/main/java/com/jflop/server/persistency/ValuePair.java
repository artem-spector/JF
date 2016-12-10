package com.jflop.server.persistency;

import java.util.Arrays;

/**
 * A pair of values with hashcode and equals implemented
 *
 * @author artem
 *         Date: 12/10/16
 */
public class ValuePair<T1, T2> {

    public final T1 value1;
    public final T2 value2;

    public ValuePair(T1 value1, T2 value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    @Override
    public int hashCode() {
        return value1.hashCode() << 1 + value2.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || !(obj instanceof ValuePair)) return false;

        ValuePair that = (ValuePair) obj;
        return Arrays.equals(
                new Object[]{value1, value2},
                new Object[]{that.value1, that.value2}
        );
    }
}
