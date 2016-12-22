package com.sample;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private void doSomeProcessing(Object data ) {
        try {
            Thread.sleep(5);
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
