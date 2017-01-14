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
    private static final int OVERHEAD_MILLIS = 3;

    private Map<String, ValuePair<FlowMockup, Float>> flows;
    private ThreadPoolExecutor threadPool;

    private long startedAt;
    private long stoppedAt;
    private boolean stopIt;
    private List<Thread> producers;
    private final ConcurrentMap<String, Integer> fireCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ValuePair<Integer, Long>> executeCount = new ConcurrentHashMap<>();


    public LoadRunner(Object[][] flowsAndThroughput) {
        flows = new HashMap<>();
        int numThreads = 1;
        for (Object[] pair : flowsAndThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            float throughput = (float) pair[1];
            flows.put(flow.getId(), new ValuePair<>(flow, throughput));
            int requiredThreads = (int) ((throughput * (flow.getExpectedDurationMillis() + OVERHEAD_MILLIS)) / 1000) + 1;
            numThreads = Math.max(numThreads, requiredThreads);
        }
        threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
    }

    public void startLoad() {
        fireCount.clear();
        executeCount.clear();

        // calculate the required number of producer threads
        float maxThroughput = 0;
        for (ValuePair<FlowMockup, Float> pair : flows.values()) {
            FlowMockup flow = pair.value1;
            float throughput = pair.value2;
            fireCount.put(flow.getId(), 0);
            executeCount.put(flow.getId(), new ValuePair<>(0, 0L));
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
            for (ValuePair<FlowMockup, Float> pair : flows.values()) {
                if (stopIt) break;

                String id = pair.value1.getId();
                Float plannedThroughput = pair.value2;
                synchronized (fireCount) {
                    int fired = fireCount.get(id);
                    if (fired + 1 <= Math.round(plannedThroughput * durationSec(System.currentTimeMillis()))) {
                        threadPool.submit(() -> {
                            long begin = System.currentTimeMillis();
                            flows.get(id).value1.go();
                            synchronized (executeCount) {
                                ValuePair<Integer, Long> countDuration = executeCount.get(id);
                                executeCount.put(id, new ValuePair<>(countDuration.value1 + 1, countDuration.value2 + System.currentTimeMillis() - begin));
                            }
                        });
                        if (!fireCount.replace(id, fired, fired + 1))
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

    public Map<String, Object[]> stopLoad(int timeoutSec) {
        stoppedAt = System.currentTimeMillis();
        stopIt = true;
        System.out.println("Thread pool: activeTasks=" + threadPool.getActiveCount() + "; queueLength=" + threadPool.getQueue().size());
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

        float loadDuration = getLoadDuration();
        Map<String, Object[]> expectedFiredCountDuration = new HashMap<>();
        for (Map.Entry<String, ValuePair<Integer, Long>> entry : executeCount.entrySet()) {
            String flowId = entry.getKey();
            int executed = entry.getValue().value1;
            long duration = entry.getValue().value2;
            int fired = fireCount.get(flowId);
            int expected = Math.round(flows.get(flowId).value2 * loadDuration);
            expectedFiredCountDuration.put(flowId, new Object[]{expected, fired, executed, duration});
        }

        return expectedFiredCountDuration;
    }

    public float getLoadDuration() {
        return durationSec(stoppedAt);
    }

    public boolean isRunning() {
        return startedAt > 0 && stoppedAt == 0;
    }

    public int getNumThreads() {
        return threadPool.getMaximumPoolSize();
    }

    private float durationSec(long to) {
        return (float) (to - startedAt) / 1000;
    }
}
