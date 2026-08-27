import java.io.*;
import java.net.*;

public class BenchmarkTest {

    private static final String HOST = "localhost";
    private static final int PORT = 1234;

    // number of clients running simultaneously
    private static final int NUM_CLIENTS = 10;

    // number of commands each client sends
    private static final int COMMANDS_PER_CLIENT = 100;

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=================================");
        System.out.println("RAMS Benchmark Test");
        System.out.println("Clients        : " + NUM_CLIENTS);
        System.out.println("Commands/Client: " + COMMANDS_PER_CLIENT);
        System.out.println("Total Commands : " + (NUM_CLIENTS * COMMANDS_PER_CLIENT));
        System.out.println("=================================");

        // create all client threads
        Thread[] clients = new Thread[NUM_CLIENTS];
        int[] successCount = new int[NUM_CLIENTS];
        int[] failCount = new int[NUM_CLIENTS];

        for (int i = 0; i < NUM_CLIENTS; i++) {
            int clientId = i;
            clients[i] = new Thread(() -> {
                try (Socket socket = new Socket(HOST, PORT);
                     BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true)) {

                    // read welcome message
                    in.readLine();

                    for (int j = 0; j < COMMANDS_PER_CLIENT; j++) {
                        String key = "client" + clientId + "_key" + j;
                        String value = "value" + j;

                        // SET command
                        out.println("SET " + key + " " + value);
                        String setResponse = in.readLine();
                        if ("OK".equals(setResponse)) {
                            successCount[clientId]++;
                        } else {
                            failCount[clientId]++;
                        }

                        // GET command
                        out.println("GET " + key);
                        String getResponse = in.readLine();
                        if (value.equals(getResponse)) {
                            successCount[clientId]++;
                        } else {
                            failCount[clientId]++;
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Client " + clientId +
                        " error: " + e.getMessage());
                }
            });
        }

        // record start time
        long startTime = System.currentTimeMillis();

        // start ALL clients at the exact same time
        System.out.println("Starting benchmark...");
        for (Thread client : clients) {
            client.start();
        }

        // wait for all clients to finish
        for (Thread client : clients) {
            client.join();
        }

        // record end time
        long endTime = System.currentTimeMillis();

        // calculate results
        long totalTime = endTime - startTime;
        int totalSuccess = 0;
        int totalFail = 0;

        for (int i = 0; i < NUM_CLIENTS; i++) {
            totalSuccess += successCount[i];
            totalFail += failCount[i];
        }

        int totalCommands = NUM_CLIENTS * COMMANDS_PER_CLIENT * 2; // SET + GET
        double opsPerSecond = (totalCommands * 1000.0) / totalTime;

        // print results
        System.out.println("=================================");
        System.out.println("BENCHMARK RESULTS");
        System.out.println("=================================");
        System.out.println("Total Commands   : " + totalCommands);
        System.out.println("Successful       : " + totalSuccess);
        System.out.println("Failed           : " + totalFail);
        System.out.println("Total Time       : " + totalTime + " ms");
        System.out.println("Ops/Second       : " + String.format("%.2f", opsPerSecond));
        System.out.println("=================================");

        if (totalFail == 0) {
            System.out.println("✓ All commands successful — no data corruption!");
        } else {
            System.out.println("✗ Some commands failed — check server logs");
        }
    }
}