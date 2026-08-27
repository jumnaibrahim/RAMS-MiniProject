import java.io.*;
import java.net.*;

public class RAMServer {
    private static final int TCP_PORT = 1234;
    private static final int WEB_PORT = 8080;
    private static MemoryStore store = new MemoryStore();
    private static WebServer webServer;

    public static void main(String[] args) {

        // load existing data from disk
        PersistenceManager persistence = new PersistenceManager(store);
        persistence.loadSnapshot();
        persistence.startAutoSave();

        // start the web dashboard
        try {
            webServer = new WebServer(store, WEB_PORT);
            webServer.start();
        } catch (IOException e) {
            System.err.println("Error starting web server: " + e.getMessage());
        }

        // start the TCP server
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[TCP] RAMS server listening on port " + TCP_PORT);
            System.out.println("Waiting for clients to connect...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("=================================");
                System.out.println("New client connected");
                System.out.println("Client IP   : " + clientSocket.getInetAddress());
                System.out.println("Client Port : " + clientSocket.getPort());
                System.out.println("=================================");

                if (webServer != null) webServer.incrementClients();

                Thread clientThread = new Thread(
                    new ClientHandler(clientSocket, store, webServer));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error in the server: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private CommandParser parser;
        private WebServer webServer;

        public ClientHandler(Socket socket, MemoryStore store, WebServer webServer) {
            this.clientSocket = socket;
            this.parser = new CommandParser(store);
            this.webServer = webServer;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(
                        clientSocket.getOutputStream(), true)) {

                out.println("Welcome to RAMS! Commands: SET key value | SET key value EX seconds | GET key | DEL key | KEYS | TTL key");

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("[Port " + clientSocket.getPort()
                        + "] Received: " + inputLine);
                    String response = parser.process(inputLine);
                    System.out.println("[Port " + clientSocket.getPort()
                        + "] Response: " + response);
                    out.println(response);

                    if (webServer != null) {
                        webServer.incrementOps();
                        webServer.logActivity(
                            "Client :" + clientSocket.getPort(),
                            inputLine,
                            response
                        );
                    }
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    if (webServer != null) webServer.decrementClients();
                    System.out.println("Client disconnected: port "
                        + clientSocket.getPort());
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
}