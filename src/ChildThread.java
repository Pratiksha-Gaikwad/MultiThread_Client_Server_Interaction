/*
 * ChildThread.java
 */


import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class ChildThread extends Thread {
    static Vector<ChildThread> handlers = new Vector<ChildThread>(20);
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    // Initialize hashmap to store user credentials.
    public static HashMap<String, String> userCred = new HashMap<>();
    // Initialize message list to store messages for MSGGET and MSGSTORE.
    public static ArrayList<String> messagesOfTheDay = ChildThread.readMessagesFromFile("messages.txt");
    // Initialize message index for MSGGET.
    public static int messageIndex = 0;
    // Initialize session flag to track logged-In users.
    public static boolean isLoggedIn = false;
    public static int loggedInUser = 0;
    public static HashMap<String, Socket> currentUsers = new HashMap<>();

    public static HashMap<String, Socket> activeUsers = new HashMap<>();

    // Initialize login credentials variables.
    String username = "";
    String password = "";
    // Store user credentials.


    public ChildThread(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()));
    }

    public void run() {
        String line;

        synchronized (handlers) {
            // add the new client in Vector class
            handlers.addElement(this);
        }

        try {
            userCred.put("root", "root01");
            userCred.put("john", "john01");
            userCred.put("david", "david01");
            userCred.put("mary", "mary01");
            while ((line = in.readLine()) != null) {
                System.out.println(line);
                // The server will confirm and terminate the client upon QUIT request.
                if ("QUIT".equals(line)) {
                    out.println("200 OK. The Client is QUIT.");
                    activeUsers.remove(username);
                    out.flush();
                    break;
                }

                // Server verifies user validity during LOGIN authentication.
                else if (line.startsWith("LOGIN")) {

                    String[] loginInfo = line.split(" ");
                    if (loginInfo.length == 3) {
                        username = loginInfo[1];
                        password = loginInfo[2];
                        if (ChildThread.authenticateUser(username, password)) {
                            // Respond with 200 OK upon the successful login.
                            if (!activeUsers.containsKey(username)) {
                                out.println("200 OK. Login successful.");
                                //isLoggedIn = true;
                                activeUsers.put(username, socket);
                                //loggedInUser = 1;
                            } else {
                                out.println(username + " already logged in.");
                            }
                        } else {
                            // Respond with 410 Wrong upon the invalid credentials.
                            out.println("410 Wrong UserID or Password.");
                        }
                    }
                    if (loginInfo.length != 3) {
                        // Respond with 400 Bad Request upon username/password missing.
                        out.println("400 Bad Request. Invalid arguments.");
                    }
                    out.flush();
                }
                // Server fetches and forwards client's daily messages upon receiving MSGGET.
                else if (line.equals("MSGGET")) {

                    if (messageIndex < messagesOfTheDay.size()) {
                        // Message at the current index is retrieved.
                        String message = messagesOfTheDay.get(messageIndex);
                        // Respond with 200 OK upon the message retrieved.
                        out.println("200 OK " + message);
                        // Increment the Index count for sequential message printing.
                        messageIndex = (messageIndex + 1) % messagesOfTheDay.size();
                    }
                    out.flush();
                }
                // The MSGSTORE command, which allows logged-in users to store a new message.
                else if (line.equals("MSGSTORE")) {
                    if (activeUsers.containsKey(username)) {
                        out.println("200 OK");
                        out.flush();
                        // Read the new message sent by the user.
                        String newMessage = in.readLine();
                        // Store the new message sent by the user in the text file.
                        ChildThread.storeMessage(newMessage);
                        // Respond with 200 OK after storing the message.
                        out.println("200 OK");
                    } else {
                        out.println("401 You are not currently logged in, login first.");
                    }
                    out.flush();
                }
                // The server will log out the client upon receiving LOGOUT from client.
                else if (line.equals("LOGOUT")) {
                    if (activeUsers.containsKey(username)) {
                        // Set session flag to false upon LOGOUT.
                        isLoggedIn = false;
                        loggedInUser = 0;
                        activeUsers.remove(username);
                        out.println("200 OK. Successfully Logout.");
                    } else {
                        out.println("401 You are not currently logged in, login first.");
                    }
                    out.flush();
                }
                // The server and client will terminate upon receiving SHUTDOWN from client.
                else if (line.equals("SHUTDOWN")) {
                    // check if user is logged in and username is root user.
                    if (activeUsers.containsKey(username) && username.equals("root")) {

                        isLoggedIn = false;
                        out.println("200 OK.");
                        for (int i = 0; i < handlers.size(); i++) {
                            synchronized (handlers) {
                                ChildThread handler =
                                        (ChildThread) handlers.elementAt(i);
                                if (handler != this) {
                                    handler.out.println("210 the server is about to shutdown.....! "); // BroadCast Message to all users connected
                                    handler.out.flush();
                                }
                            }
                        }
                        System.exit(1);
                    } else {
                        out.println("402 User not allowed to execute this command.");
                        System.out.println(line);
                    }
                    out.flush();
                }
                // The server will notify list of Active Users.
                else if (line.equals("WHO")) {
                    if (activeUsers.isEmpty()) {
                        out.println("No active users.");
                    } else {
                        HashMap<String, String> allActiveUsers = new HashMap<>();
                        for (Map.Entry<String, Socket> entry : activeUsers.entrySet()) {
                            String username = entry.getKey();
                            Socket userSocket = entry.getValue();
                            String ipAddress = userSocket.getInetAddress().getHostAddress();
                            allActiveUsers.put(username, ipAddress);
                        }
                        out.println(allActiveUsers);
                    }
                    out.flush();
                    // It allows users to Send message to target Users
                } else if (line.startsWith("SEND")) {
                    String[] sendInfo = line.split(" ");
                    System.out.println(sendInfo.length);
                    if (sendInfo.length == 2) {
                        String receiverUser = sendInfo[1];
                        if (activeUsers.containsKey(username)) {
                            if (activeUsers.containsKey(receiverUser)) {
                                Socket targetSocket = activeUsers.get(receiverUser);
                                //Forward the message to the target user's socket.
                                PrintStream targetOs = new PrintStream(targetSocket.getOutputStream());
                                targetOs.println("200 OK you have a new message from " + username);
                                out.println("200 OK");
                                out.flush();
                                String message = in.readLine();
                                targetOs.println(username + ": " + message);
                                out.println("200 OK");

                            } else {
                                out.println("420 either the user does not exist or is not logged in");
                            }
                        } else {
                            out.println("401 You are not currently logged in. Login first.");
                        }

                    } else {
                        out.println("400 Bad Request. Invalid arguments.");
                    }
                    out.flush();
                } else {
                    System.out.println(line);
                    out.println("300 Message Format Error");
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
                socket.close();
            } catch (IOException ioe) {
            } finally {
                synchronized (handlers) {
                    handlers.removeElement(this);
                }
            }
        }


    }

    // Function to authenticate a user based on their username and password
    private static boolean authenticateUser(String username, String password) {
        String storedPassword = userCred.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    // Function to read messages from a text file
    private static ArrayList<String> readMessagesFromFile(String fileName) {
        // ArrayList to store messages.
        ArrayList<String> messages = new ArrayList<>();
        // To read file, open it as a FileReader and wrap it in a BufferedReader.
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            //store each line read from the file.
            String line;
            while ((line = br.readLine()) != null) {
                messages.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading messages from file: " + e.getMessage());
        }
        return messages;
    }

    // Function to store a new message in a text file
    private static void storeMessage(String message) {
        try (FileWriter fw = new FileWriter("messages.txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(message);

        } catch (IOException e) {
            System.err.println("Error storing message: " + e.getMessage());
        }
    }
}


