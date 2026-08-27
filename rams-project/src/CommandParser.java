public class CommandParser {

    private MemoryStore store;

    public CommandParser(MemoryStore store) {
        this.store = store;
    }

    public String process(String command) {
        String[] parts = command.trim().split("\\s+");

        if (parts.length == 0 || command.trim().isEmpty()) {
            return "ERROR: Empty command";
        }

        String operation = parts[0].toUpperCase();

        switch (operation) {

            case "SET":
                // SET key value
                if (parts.length < 3) {
                    return "ERROR: SET requires a key and value. Usage: SET key value";
                }
                // SET key value EX seconds
                if (parts.length == 5 && parts[3].toUpperCase().equals("EX")) {
                    try {
                        int seconds = Integer.parseInt(parts[4]);
                        return store.setWithTTL(parts[1], parts[2], seconds);
                    } catch (NumberFormatException e) {
                        return "ERROR: EX requires a valid number of seconds";
                    }
                }
                return store.set(parts[1], parts[2]);

            case "GET":
                if (parts.length < 2) {
                    return "ERROR: GET requires a key. Usage: GET key";
                }
                return store.get(parts[1]);

            case "DEL":
                if (parts.length < 2) {
                    return "ERROR: DEL requires a key. Usage: DEL key";
                }
                return store.delete(parts[1]);

            case "KEYS":
                return store.keys();

            case "TTL":
                // TTL key → shows how many seconds left before expiry
                if (parts.length < 2) {
                    return "ERROR: TTL requires a key. Usage: TTL key";
                }
                return store.ttl(parts[1]);

            default:
                return "ERROR: Unknown command: " + operation;
        }
    }
}