import java.io.*;
import java.net.*;

public class RAMSClient {
    private static final String HOST = "localhost";
    private static final int PORT = 1234;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true)) {

            // read and print the welcome message FIRST
            // before entering the command loop
            String welcome = in.readLine();
            System.out.println(welcome);
            System.out.println("---------------------------");

            BufferedReader userInput = new BufferedReader(
                new InputStreamReader(System.in));

            String inputLine;
            while ((inputLine = userInput.readLine()) != null) {
                // send command to server
                out.println(inputLine);

                // immediately read and print the response
                String response = in.readLine();
                System.out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Error in the client: " + e.getMessage());
        }
    }
}