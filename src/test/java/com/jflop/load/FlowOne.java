package com.jflop.load;

/**
 * TODO: Document!
 *
 * @author artem on 10/01/2017.
 */
public class FlowOne implements FlowMockup {

    private String name;
    private long sleepDuration;

    public FlowOne(String name, long sleepDurationMillis) {
        this.name = name;
        this.sleepDuration = sleepDurationMillis;
    }

    @Override
    public String getId() {
        return name;
    }

    @Override
    public long getExpectedDurationMillis() {
        return sleepDuration;
    }

    @Override
    public void go() {
        napForAWhile(sleepDuration);
    }

    public void napForAWhile(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
