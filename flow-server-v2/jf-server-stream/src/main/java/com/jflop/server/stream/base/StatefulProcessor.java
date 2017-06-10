package com.jflop.server.stream.base;

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.lang.reflect.Field;
import java.util.*;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 5/21/17
 */
public abstract class StatefulProcessor<K, V> implements Processor<K, V> {

    private static Map<Class<? extends StatefulProcessor>, List<Field>> stateFields = new HashMap<>();

    public final String processorId;

    protected ProcessorContext context;

    protected StatefulProcessor(String processorId) {
        this.processorId = processorId;
    }

    /**
     * Get the values of the fields annotated as ProcessorState, instantiate if null.
     * Fields of the same class will be populated with the same value
     *
     * @return collection of state values
     */
    public Collection<TimeWindowStateStore> getStateFields() {
        Map<Class, TimeWindowStateStore> res = new HashMap<>();
        for (Field field : getAllStateFields()) {
            try {
                Object value = field.get(this);
                if (value == null) {
                    value = res.get(field.getType());
                    if (value == null) value = field.getType().newInstance();
                    field.set(this, value);
                }
                res.put(field.getType(), (TimeWindowStateStore) value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access value of field " + field.getName(), e);
            } catch (InstantiationException e) {
                throw new RuntimeException("Failed to instantiate field " + field.getName(), e);
            }
        }
        return res.values();
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        for (TimeWindowStateStore state : getStateFields()) state.init(this);
    }

    private List<Field> getAllStateFields() {
        Class<? extends StatefulProcessor> cls = getClass();
        synchronized (cls) {
            return stateFields.computeIfAbsent(cls, this::getAllStateFields);
        }
    }

    private List<Field> getAllStateFields(Class cls) {
        if (cls == StatefulProcessor.class) return Collections.EMPTY_LIST;

        List<Field> res = new ArrayList<>();
        for (Field field : Arrays.asList(cls.getDeclaredFields())) {
            if (field.getAnnotation(ProcessorState.class) != null) {
                if (!TimeWindowStateStore.class.isAssignableFrom(field.getType()))
                    throw new RuntimeException("Field '" + field.getName() + "' is annotated with @ProcessorState, but its type is not " + TimeWindowStateStore.class.getName());
                field.setAccessible(true);
                res.add(field);
            }
        }

        res.addAll(getAllStateFields(cls.getSuperclass()));
        return res;
    }
}
