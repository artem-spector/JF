package com.jflop.server.stream.base;

import org.apache.kafka.streams.processor.ProcessorSupplier;

import java.util.Properties;

/**
 * TODO: Document!
 *
 * @author artem
 *         Date: 28/05/2017
 */
public class TestStreamsApplication extends StreamsApplication {

    public TestStreamsApplication(String appId, Properties topologyProperties, AutoOffsetReset autoOffsetReset) {
        super(appId, topologyProperties, autoOffsetReset);
    }

    @Override
    protected ProcessorSupplier getProcessorSupplier(Class<? extends StatefulProcessor> processorClass) {
        return () -> {
            try {
                StatefulProcessor instance = processorClass.newInstance();
                return new TestStatefulProcessor(instance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate processor", e);
            }
        };
    }
}
