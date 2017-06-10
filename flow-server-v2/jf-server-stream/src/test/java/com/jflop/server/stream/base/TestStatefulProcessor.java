package com.jflop.server.stream.base;

import org.apache.kafka.streams.processor.ProcessorContext;

import java.util.Collection;
import java.util.Map;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 28/05/2017
 */
public class TestStatefulProcessor extends StatefulProcessor {

    public static final String CLEAR_STATE_STORESS = "clearStateStoress";

    private StatefulProcessor delegate;

    public TestStatefulProcessor(StatefulProcessor delegate) {
        super(delegate.processorId);
        this.delegate = delegate;
    }

    @Override
    public void init(ProcessorContext context) {
        delegate.init(context);
    }

    @Override
    public void process(Object key, Object value) {
        String clearStates = (String) ((Map) value).get(CLEAR_STATE_STORESS);
        if ("true".equalsIgnoreCase(clearStates))
            clearStates();
        else
            delegate.process(key, value);
    }

    @Override
    public void punctuate(long timestamp) {
        delegate.punctuate(timestamp);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public Collection<TimeWindowStateStore> getStateFields() {
        return delegate.getStateFields();
    }

    private void clearStates() {
        for (TimeWindowStateStore store : getStateFields()) {
            store.clear();
        }
    }
}
