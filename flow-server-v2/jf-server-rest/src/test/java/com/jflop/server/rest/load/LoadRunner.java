package com.jflop.server.rest.load;


import com.jflop.server.rest.persistency.ValuePair;

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
    private static final int OVERHEAD_MILLIS = 20;

    private Map<String, ValuePair<FlowMockup, Float>> flows;
    private ThreadPoolExecutor threadPool;

    private long startedAt;
    private long stoppedAt;
    private volatile boolean stopIt;
    private List<Thread> producers;
    private final ConcurrentMap<String, Integer> fireCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ValuePair<Integer, Long>> executeCount = new ConcurrentHashMap<>();


    public LoadRunner(Object[][] flowsAndThroughput) {
        flows = new HashMap<>();
        float totalDurationPerSec = 0;
        for (Object[] pair : flowsAndThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            float throughput = (float) pair[1];
            flows.put(flow.getId(), new ValuePair<>(flow, throughput));
            totalDurationPerSec += ((float) flow.getExpectedDurationMillis() + OVERHEAD_MILLIS) / 1000 * throughput;
        }
        int numThreads = (int) totalDurationPerSec + 1;
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
                int expected = Math.round(plannedThroughput * durationSec(System.currentTimeMillis()));
                int fired = fireCount.get(id);
                int executed = executeCount.get(id).value1;
                if (fired < expected) {
                    threadPool.submit(() -> {
                        if (!stopIt) {
                            long begin = System.currentTimeMillis();
                            flows.get(id).value1.go();

                            if (!stopIt) {
                                boolean updated = false;
                                while (!updated) {
                                    ValuePair<Integer, Long> countDuration = executeCount.get(id);
                                    updated = executeCount.replace(id, countDuration, new ValuePair<>(countDuration.value1 + 1, countDuration.value2 + System.currentTimeMillis() - begin));
                                }
                            }
                        }
                    });
                    boolean updated = false;
                    while (!updated) {
                        fired = fireCount.get(id);
                        updated = fireCount.replace(id, fired, fired + 1);
                    }
                }
            }

            if (!stopIt) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public LoadResult stopLoad(int timeoutSec) {
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

        return getLoadResult(stoppedAt);
    }

    public LoadResult getLoadResult(long toTime) {
        LoadResult res = new LoadResult();
        float loadDuration = durationSec(toTime);
        res.durationMillis = toTime - startedAt;
        res.flows = new HashMap<>();
        res.numThreads = threadPool.getMaximumPoolSize();

        for (Map.Entry<String, ValuePair<Integer, Long>> entry : executeCount.entrySet()) {
            String flowId = entry.getKey();
            int executed = entry.getValue().value1;
            long duration = entry.getValue().value2;
            int fired = fireCount.get(flowId);
            int expected = Math.round(flows.get(flowId).value2 * loadDuration);
            res.flows.put(flowId, new FlowStats(expected, fired, executed, (float) duration / executed));
        }
        return res;
    }

    public boolean isRunning() {
        return startedAt > 0 && stoppedAt == 0;
    }

    private float durationSec(long to) {
        return (float) (to - startedAt) / 1000;
    }

    public static List<String> validateResult(LoadResult res, Object[][] flowsThroughput) {
        List<String> problems = new ArrayList<>();
        for (Object[] pair : flowsThroughput) {
            FlowMockup flow = (FlowMockup) pair[0];
            String flowId = flow.getId();
            LoadRunner.FlowStats stats = res.flows.get(flowId);
            System.out.println(flowId + " in " + res.numThreads + " threads: expected=" + stats.expected + "; fired=" + stats.fired + "; executed=" + stats.executed
                    + "\n\t duration: expected=" + flow.getExpectedDurationMillis() + "; actual=" + stats.averageDuration);

            if (Math.abs(stats.expected - stats.executed) > Math.max(1, stats.expected / 15))
                problems.add("Problematic flow: expected=" + stats.expected + "; executed=" + stats.executed + "\n" + flow.toString());
        }
        return problems;
    }

    public static class LoadResult {
        public long durationMillis;
        public Map<String, FlowStats> flows;
        public int numThreads;
    }

    public static class FlowStats {

        public int expected;
        public int fired;
        public int executed;
        public float averageDuration;

        public FlowStats() {
        }

        public FlowStats(int expected, int fired, int executed, float averageDuration) {
            this.expected = expected;
            this.fired = fired;
            this.executed = executed;
            this.averageDuration = averageDuration;
        }
    }
}
