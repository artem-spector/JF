package com.jflop.load;

/**
 * Mockup flow contract
 *
 * @author artem on 10/01/2017.
 */
public interface FlowMockup {

    String getId();

    long getExpectedDurationMillis();

    void go();
}
