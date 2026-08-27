import java.util.concurrent.ConcurrentHashMap;

public class MemoryStore {

    // stores the actual values
    private ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    // stores the expiry time for each key
    // System.currentTimeMillis() gives time in milliseconds
    // we store the exact millisecond when the key should expire
    private ConcurrentHashMap<String, Long> expiryTimes = new ConcurrentHashMap<>();

    public MemoryStore() {
        // start the background thread that checks for expired keys
        startExpiryThread();
    }

    // SET: store a key-value pair
    public String set(String key, String value) {
        store.put(key, value);
        // remove any existing expiry when key is reset
        expiryTimes.remove(key);
        return "OK";
    }

    // SET with TTL: store a key-value pair with expiry time in seconds
    public String setWithTTL(String key, String value, int seconds) {
        store.put(key, value);
        // calculate exact millisecond when this key should expire
        // e.g. current time + 10 seconds = expiry time
        long expiryTime = System.currentTimeMillis() + (seconds * 1000L);
        expiryTimes.put(key, expiryTime);
        return "OK";
    }

    // GET: retrieve a value by key
    public String get(String key) {
        // first check if key has expired
        if (isExpired(key)) {
            store.remove(key);
            expiryTimes.remove(key);
            return "(nil)";
        }

        String value = store.get(key);
        if (value == null) {
            return "(nil)";
        }
        return value;
    }

    // DEL: delete a key-value pair
    public String delete(String key) {
        if (store.containsKey(key)) {
            store.remove(key);
            expiryTimes.remove(key);
            return "OK";
        }
        return "(nil)";
    }

    // KEYS: list all keys currently in the store
    public String keys() {
        if (store.isEmpty()) {
            return "(empty)";
        }
        return store.keySet().toString();
    }

    // TTL: check how many seconds a key has left before expiry
    public String ttl(String key) {
        if (!store.containsKey(key)) {
            return "-2"; // key doesn't exist
        }
        if (!expiryTimes.containsKey(key)) {
            return "-1"; // key exists but has no expiry
        }
        long remainingTime = expiryTimes.get(key) - System.currentTimeMillis();
        if (remainingTime <= 0) {
            return "-2"; // key has expired
        }
        // convert milliseconds back to seconds
        return String.valueOf(remainingTime / 1000);
    }

    // checks if a key has expired
    private boolean isExpired(String key) {
        if (!expiryTimes.containsKey(key)) {
            return false; // no expiry set
        }
        return System.currentTimeMillis() > expiryTimes.get(key);
    }

    // background thread that runs every second
    // finds and removes all expired keys automatically
    private void startExpiryThread() {
        Thread expiryThread = new Thread(() -> {
            while (true) {
                try {
                    // check every 1 second
                    Thread.sleep(1000);

                    // go through all keys that have expiry times
                    for (String key : expiryTimes.keySet()) {
                        if (isExpired(key)) {
                            store.remove(key);
                            expiryTimes.remove(key);
                            System.out.println("[EXPIRY] Key '" + key + "' has expired and was removed");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // daemon thread means it stops automatically when server stops
        // you don't have to manually kill it
        expiryThread.setDaemon(true);
        expiryThread.start();
        System.out.println("[EXPIRY] Background expiry thread started");
    }
    // expose store for persistence manager
public ConcurrentHashMap<String, String> getStore() {
    return store;
}

// expose expiry times for persistence manager
public ConcurrentHashMap<String, Long> getExpiryTimes() {
    return expiryTimes;
}
}