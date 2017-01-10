package com.jflop.load;

import com.jflop.server.persistency.ValuePair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Runs given flows with required throughput.
 *
 * @author artem on 10/01/2017.
 */
public class LoadRunner {

    private static final Logger logger = Logger.getLogger(LoadRunner.class.getName());

    private Map<String, ValuePair<FlowMockup, Float>> flows = new HashMap<>();

    private ExecutorService threadPool;
    private long startedAt;
    private long stoppedAt;
    private boolean stopIt;
    private List<Thread> producers;
    private final ConcurrentMap<String, Integer> fireCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> executeCount = new ConcurrentHashMap<>();

    public void addFlow(FlowMockup flowMockup, float throughput) {
        flows.put(flowMockup.getId(), new ValuePair<>(flowMockup, throughput));
    }

    public void startLoad(int numThreads) {
        threadPool = Executors.newFixedThreadPool(numThreads);
        fireCount.clear();
        executeCount.clear();

        // calculate the required number of producer threads
        float maxThroughput = 0;
        for (ValuePair<FlowMockup, Float> pair : flows.values()) {
            FlowMockup flow = pair.value1;
            float throughput = pair.value2;
            fireCount.put(flow.getId(), 0);
            executeCount.put(flow.getId(), 0);
            maxThroughput = Math.max(maxThroughput, throughput);
        }
        int numProducers = Math.max(1, (int) (maxThroughput / 500));
        System.out.println(numProducers + " producer threads.");
        producers = new ArrayList<>();
        for (int i = 0; i < numProducers; i++) producers.add(new Thread(this::fireFlows));

        stopIt = false;
        startedAt = System.currentTimeMillis();
        stoppedAt = 0;
        producers.forEach(Thread::start);
    }

    private void fireFlows() {
        while (!stopIt) {
            float durationSec = durationSec(System.currentTimeMillis());
            for (ValuePair<FlowMockup, Float> pair : flows.values()) {
                if (stopIt) break;

                String id = pair.value1.getId();
                Float plannedThroughput = pair.value2;
                if (fireCount.get(id) + 1 <= plannedThroughput * durationSec) {
                    threadPool.submit(() -> {
                        flows.get(id).value1.go();
                        synchronized (executeCount) {
                            Integer current = executeCount.get(id);
                            if (!executeCount.replace(id, current, current + 1))
                                logger.severe("Failed to update execute count for flow " + id);
                        }
                    });
                    synchronized (fireCount) {
                        Integer current = fireCount.get(id);
                        if (!fireCount.replace(id, current, current + 1))
                            logger.severe("Failed to update fire count for flow " + id);
                    }
                }
            }

            if (!stopIt) {
                try {
                    Thread.sleep(0, 1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public void stopLoad(int timeoutSec) {
        stoppedAt = System.currentTimeMillis();
        stopIt = true;

        producers.forEach(Thread::interrupt);
        producers.forEach((thread) -> {
            try {
                thread.join(timeoutSec * 1000);
            } catch (InterruptedException e) {
                // ignore
            }
            if (thread.isAlive())
                logger.warning("Producer did not stop in " + timeoutSec + " sec.");
        });

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(timeoutSec, TimeUnit.SECONDS))
                logger.warning("Load has not terminated in " + timeoutSec + " sec.");
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public ValuePair<Float, Float> getExpectedAndActualThroughput(String flowId) {
        assert stoppedAt > 0;
        return new ValuePair<>(flows.get(flowId).value2, (float) executeCount.get(flowId) / getLoadDuration());
    }

    public float getLoadDuration() {
        return durationSec(stoppedAt);
    }

    private float durationSec(long to) {
        return (float) (to - startedAt) / 1000;
    }
}
