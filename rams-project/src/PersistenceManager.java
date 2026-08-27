import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class PersistenceManager {

    // the file where we save the data
    private static final String SNAPSHOT_FILE = "rams_snapshot.txt";

    // how often we save to disk (in milliseconds)
    // 30 seconds = 30000 milliseconds
    private static final int SAVE_INTERVAL = 30000;

    private MemoryStore store;

    public PersistenceManager(MemoryStore store) {
        this.store = store;
    }

    // save all data to disk
    // format: key=value
    // e.g:
    // name=Siva
    // age=23
    public void saveSnapshot() {
        try (PrintWriter writer = new PrintWriter(
                new FileWriter(SNAPSHOT_FILE))) {

            // get all current data from store
            ConcurrentHashMap<String, String> data = store.getStore();
            ConcurrentHashMap<String, Long> expiryTimes = store.getExpiryTimes();

            for (String key : data.keySet()) {
                String value = data.get(key);
                Long expiryTime = expiryTimes.get(key);

                // if key has expiry time, save it too
                // format: key=value=expiryTime
                if (expiryTime != null) {
                    // only save if key hasn't expired yet
                    if (expiryTime > System.currentTimeMillis()) {
                        writer.println(key + "=" + value + "=" + expiryTime);
                    }
                } else {
                    // no expiry time
                    writer.println(key + "=" + value);
                }
            }

            System.out.println("[PERSISTENCE] Snapshot saved to " + SNAPSHOT_FILE);

        } catch (IOException e) {
            System.err.println("[PERSISTENCE] Error saving snapshot: " + e.getMessage());
        }
    }

    // load data from disk back into memory
    public void loadSnapshot() {
        File file = new File(SNAPSHOT_FILE);

        // if no snapshot file exists yet, skip loading
        if (!file.exists()) {
            System.out.println("[PERSISTENCE] No snapshot file found. Starting fresh.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");

                if (parts.length == 2) {
                    // no expiry time
                    store.set(parts[0], parts[1]);
                    count++;
                } else if (parts.length == 3) {
                    // has expiry time
                    String key = parts[0];
                    String value = parts[1];
                    long expiryTime = Long.parseLong(parts[2]);

                    // only load if key hasn't expired yet
                    if (expiryTime > System.currentTimeMillis()) {
                        int remainingSeconds = (int)((expiryTime - 
                            System.currentTimeMillis()) / 1000);
                        store.setWithTTL(key, value, remainingSeconds);
                        count++;
                    }
                }
            }

            System.out.println("[PERSISTENCE] Loaded " + count + 
                " keys from snapshot.");

        } catch (IOException e) {
            System.err.println("[PERSISTENCE] Error loading snapshot: " 
                + e.getMessage());
        }
    }

    // background thread that saves snapshot every 30 seconds
    public void startAutoSave() {
        Thread autoSaveThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(SAVE_INTERVAL);
                    saveSnapshot();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // daemon thread stops automatically when server stops
        autoSaveThread.setDaemon(true);
        autoSaveThread.start();
        System.out.println("[PERSISTENCE] Auto-save thread started. " + 
            "Saving every 30 seconds.");
    }
}