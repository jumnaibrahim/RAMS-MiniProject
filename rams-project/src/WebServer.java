import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WebServer {

    private MemoryStore store;
    private CommandParser parser;
    private int port;
    private long serverStartTime;
    private int totalOps = 0;
    private int connectedClients = 0;
    private java.util.Set<String> httpClientsSeen = 
    java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private ConcurrentLinkedQueue<String> activityLog = new ConcurrentLinkedQueue<>();
    private int maxLogSize = 50;

    public WebServer(MemoryStore store, int port) {
        this.store = store;
        this.parser = new CommandParser(store);
        this.port = port;
        this.serverStartTime = System.currentTimeMillis();
    }

    public void incrementClients() { connectedClients++; }
    public void decrementClients() { connectedClients--; }
    public void incrementOps() { totalOps++; }
    public int getTotalOps() { return totalOps; }

    public void logActivity(String clientId, String command, String response) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss")
            .format(new java.util.Date());
        String entry = "{\"time\":\"" + time +
            "\",\"client\":\"" + escapeJson(clientId) +
            "\",\"command\":\"" + escapeJson(command) +
            "\",\"response\":\"" + escapeJson(response) + "\"}";
        activityLog.add(entry);
        while (activityLog.size() > maxLogSize) {
            activityLog.poll();
        }
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // serve dashboard HTML
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                File file = new File("dashboard.html");
                if (file.exists()) {
                    byte[] content = Files.readAllBytes(file.toPath());
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, content.length);
                    exchange.getResponseBody().write(content);
                } else {
                    String msg = "dashboard.html not found";
                    exchange.sendResponseHeaders(404, msg.length());
                    exchange.getResponseBody().write(msg.getBytes());
                }
                exchange.getResponseBody().close();
            }
        });

        // API: execute a command
        server.createContext("/api/command", exchange -> {
            setCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                String command = body.trim();
                String response = parser.process(command);
                totalOps++;
                logActivity("Browser", command, response);
                sendJson(exchange, "{\"command\":\"" + escapeJson(command) +
                    "\",\"response\":\"" + escapeJson(response) + "\"}");
            } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
            }
        });
        // API: execute command as a named client
        server.createContext("/api/client-command", exchange -> {
            setCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                // format: clientName|command
                String[] parts = body.split("\\|", 2);
                if (parts.length == 2) {
                    String clientName = parts[0].trim();
                        String command = parts[1].trim();
                        String response = parser.process(command);
                        totalOps++;
                        httpClientsSeen.add(clientName);
                        logActivity(clientName, command, response);
                    sendJson(exchange, "{\"command\":\"" + escapeJson(command) +
                        "\",\"response\":\"" + escapeJson(response) + "\"}");
                }
            } else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
            }
        });

        // API: get server stats
        server.createContext("/api/stats", exchange -> {
            setCors(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                ConcurrentHashMap<String, String> data = store.getStore();
                long uptime = (System.currentTimeMillis() - serverStartTime) / 1000;
                String json = "{" +
                    "\"totalKeys\":" + data.size() + "," +
                    "\"tcpClients\":" + connectedClients + "," +
                    "\"httpSessions\":" + httpClientsSeen.size() + "," +
                    "\"totalOps\":" + totalOps + "," +
                    "\"uptime\":" + uptime + "}";
                sendJson(exchange, json);
            }
        });

        // API: get all keys with values and TTL
        server.createContext("/api/keys", exchange -> {
            setCors(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                ConcurrentHashMap<String, String> data = store.getStore();
                ConcurrentHashMap<String, Long> expiryTimes = store.getExpiryTimes();
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (String key : data.keySet()) {
                    if (!first) json.append(",");
                    first = false;
                    String value = data.get(key);
                    Long expiry = expiryTimes.get(key);
                    String ttl = "-";
                    if (expiry != null) {
                        long remaining = (expiry - System.currentTimeMillis()) / 1000;
                        ttl = remaining > 0 ? remaining + "s" : "expired";
                    }
                    json.append("{\"key\":\"").append(escapeJson(key))
                        .append("\",\"value\":\"").append(escapeJson(value))
                        .append("\",\"ttl\":\"").append(ttl).append("\"}");
                }
                json.append("]");
                sendJson(exchange, json.toString());
            }
        });

        // API: get activity log
        server.createContext("/api/activity", exchange -> {
            setCors(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (String entry : activityLog) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(entry);
                }
                json.append("]");
                sendJson(exchange, json.toString());
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[WEB] Dashboard running at http://localhost:" + port);
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private void setCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}