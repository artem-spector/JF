package com.sample;

import com.jflop.HttpTestClient;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * This is an example of an application code which can work in multiple flows.
 * It simulates a situation with cache initialization.
 * <p>
 * Note the synchronized method. Try to remove this keyword and compare the profiling results
 *
 * @author artem
 */
public class MultipleFlowsProducer {

    private int maxCacheSize = 5;
    private Map<String, Object> cache = new LinkedHashMap<>();
    private Random random = new Random();

    public void serve(String user) {
        Object data = getUserData(user);
        doSomeProcessing(data);
    }

    private synchronized Object getUserData(String user) {
        Object data = cache.get(user);
        if (data == null) {
            data = initUserData(user);
            if (cache.size() > maxCacheSize) cache.clear();
            cache.put(user, data);
        }
        return data;
    }

    private Object initUserData(String user) {
        return new UserData(user);
    }

    private void doSomeProcessing(Object data) {
        switch (random.nextInt(3)) {
            case 0:
                sleep(10);
                break;
            case 1:
                httpGet("http://www.google.com");
                break;
            case 2:
                busyLoop(1000000);
                break;
            default:
                throw new RuntimeException("unsupported processing choice");
        }
    }

    private double busyLoop(int count) {
        long begin = System.currentTimeMillis();
        double v = random.nextDouble();
        double res = 0;
        for (int i = 0; i < count; i++) {
            res += Math.pow(v, count);
        }
        long duration = System.currentTimeMillis() - begin;
        return res;
    }

    private HttpTestClient.Response httpGet(String url) {
        try {
            return new HttpTestClient(url).send(new HttpTestClient.Request(HttpMethod.GET, ""));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public static class UserData {

        public final String data;

        public UserData(String id) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
            data = "This is data for user " + id;
        }
    }

}
