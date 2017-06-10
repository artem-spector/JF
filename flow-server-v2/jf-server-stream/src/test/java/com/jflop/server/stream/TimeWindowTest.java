package com.jflop.server.stream;

import com.jflop.server.data.JacksonSerdes;
import com.jflop.server.stream.base.SlidingWindow;
import com.jflop.server.stream.base.TimeWindow;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.kafka.common.serialization.Serde;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;

import static org.junit.Assert.assertEquals;

/**
 * TODO: Document!
 *
 * @author artem on 18/05/2017.
 */
public class TimeWindowTest {

    @Test
    public void testSize() {
        long maxSizeMs = 10;
        TimeWindow<String> window = new TimeWindow<>();
        window.init(maxSizeMs);
        int first = 1;
        int last = 100;
        for (int i = first; i <= last; i++) {
            window.putValue(i, "V" + i);
        }

        NavigableMap<Long, String> values = window.getValues(0, Long.MAX_VALUE);
        assertEquals(window.toString(), maxSizeMs + 1, values.size());
        assertEquals(window.toString(), last, (long)values.lastKey());
        assertEquals(window.toString(), last - maxSizeMs, (long)values.firstKey());
    }

    @Test
    public void testSubset() {
        long maxSizeMs = 100;
        TimeWindow<String> window = new TimeWindow<>();
        window.init(maxSizeMs);
        int first = 30;
        int last = first + (int) maxSizeMs;
        for (int i = first; i <= last; i++) {
            window.putValue(i, "V" + i);
        }

        assertEquals(maxSizeMs + 1, window.getValues(first, last).size());
        assertEquals(maxSizeMs + 1, window.getValues(first - 10, last + 10).size());
        assertEquals(maxSizeMs - 1, window.getValues(first + 1, last - 1).size());
        assertEquals(0, window.getValues(first - 2, first - 1).size());
        assertEquals(0, window.getValues(last + 1, last + 2).size());
    }

    @Test
    public void testCustomDataClass() throws IOException {
        TimeWindow<MyDataClass> window = new TimeWindow<>();
        window.init(1000L);
        MyDataClass value = new MyDataClass("aName", 1.234f, "line one", "line two", "l3");
        window.putValue(0, value);

        Serde<TimeWindow<MyDataClass>> serde = JacksonSerdes.jacksonSerde(new TypeReference<TimeWindow<MyDataClass>>() { });
        byte[] bytes = serde.serializer().serialize("a", window);
        window = serde.deserializer().deserialize("a", bytes);
        assertEquals(value, window.getValue(0));
    }

    @Test
    public void testSlidingWindow() {
        long maxSizeMs = 30;
        TimeWindow<String> window = new TimeWindow<>();
        window.init(maxSizeMs);
        int first = 1;
        int last = 100;
        int prevNextNum = 5;

        for (int i = first; i <= last; i++) {
//            System.out.println(i + " --------------------");
            window.putValue(i, "V" + i);

            int numRecent = (int) (i <= maxSizeMs / 2 ? i : (maxSizeMs / 2) + 1);
            NavigableMap<Long, String> recentValues = window.getRecentValues();
            assertEquals(numRecent, recentValues.size());

            SlidingWindow<String> slidingWindow = new SlidingWindow<>(recentValues);
            slidingWindow.scanValues(prevNextNum, prevNextNum, (value, prevValues, nextValues) -> {
//                System.out.println("   " + value + "; prev:" + prevValues.size() + "; next:" + nextValues.size());
                int size = Math.min(numRecent / 2, prevNextNum);
                assertEquals(size, prevValues.size());
                assertEquals(size, nextValues.size());
            });
        }
    }

    public static class MyDataClass {
        public String name;
        public float floatValue;
        public List<String> textLines;

        public MyDataClass() {
        }

        public MyDataClass(String name, float floatValue, String... textLines) {
            this.name = name;
            this.floatValue = floatValue;
            this.textLines = Arrays.asList(textLines);
        }

        @Override
        public int hashCode() {
            return name.hashCode() + (int) floatValue + textLines.size();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || !(obj instanceof MyDataClass)) return false;
            MyDataClass that = (MyDataClass) obj;
            return Arrays.equals(new Object[] {name, floatValue, textLines}, new Object[]{that.name, that.floatValue, that.textLines});
        }
    }
}
