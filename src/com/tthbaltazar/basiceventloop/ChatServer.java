package com.tthbaltazar.basiceventloop;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
    Simple chat server showing how to do IO with events.
*/
public class ChatServer {
    /*
        Core of the event loop.
    */

    /**
        A queue of {@link Runnable}s that can be access from other threads.

        Most events will be coming from other threads that will do blocking operations.
    */
    private final static LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

    /** Simple wrapper around the queue. */
    public static void addTask(Runnable task) {
        tasks.add(task);
    }

    /**
        The event loop.
    */
    public static void runEventLoop() throws InterruptedException {
        while (true) {
            Runnable t = tasks.take();
            t.run();
        }
    }

    private static class SocketAcceptor {
        public interface SocketAcceptorCallback {
            void onAccepted(SocketAcceptor acceptor, Socket s);
        }

        public SocketAcceptor(ServerSocket serverSocket, SocketAcceptorCallback cb) {
            new Thread(() -> {
                while(true) {
                    try {
                        Socket s = serverSocket.accept();

                        addTask(() -> {
                            // runs on main thread
                            cb.onAccepted(SocketAcceptor.this, s);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    /**
        Simple class wrapping a {@link Socket}.

        Provides a background thread, with callbacks to the main thread about received text lines and disconnecting.
    */
    private static class ChatClient {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        public ChatClient(Socket s, ChatClientReaderCallback cb) throws IOException {
            writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(s.getInputStream()));

            new Thread(() -> {
                try {
                    while(true) {
                        String line = reader.readLine();

                        if (line == null) {
                            cb.onDisconnect(this);
                            break;
                        }

                        addTask(() -> {
                            cb.onReadLine(this, line);
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        public void SendLine(String s) throws IOException {
            try {
                writer.write(s);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                throw new IOException("Client disconnected", e);
            }
        }

        public interface ChatClientReaderCallback {
            void onReadLine(ChatClient client, String line);
            void onDisconnect(ChatClient client);
        }
    }

    //  Simple counter for client IDs
    private int clientIdCounter = 0;

    //  Keep a list of clients around to broadcast messages
    private final ArrayList<ChatClient> clients = new ArrayList<>();

    //  Runs for every incoming connection
    private final SocketAcceptor.SocketAcceptorCallback serverCallback = (ignored, s) -> {
        int id = clientIdCounter++;

        System.out.println("Client <"+id+"> connected");

        try {
            ChatClient client = new ChatClient(s, new ChatClient.ChatClientReaderCallback() {
                @Override
                public void onReadLine(ChatClient client, String line) {
                    String decorated = "Client <"+id+"> said: " + line;

                    System.out.println(decorated);

                    for (ChatClient c : clients) {
                        if (c == client) continue;

                        try {
                            c.SendLine(decorated);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onDisconnect(ChatClient client) {
                    String decorated = "Client <"+id+"> disconnected";

                    System.out.println(decorated);

                    //  We are on the main thread so it's safe to touch the list
                    clients.remove(client);

                    for (ChatClient c : clients) {
                        if (c == client) continue;

                        try {
                            c.SendLine(decorated);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            //  We are on the main thread so it's safe to touch the list
            clients.add(client);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                s.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    };

    private static final int PORT = 5000;
    public ChatServer() {
        ServerSocket ss;
        try {
            ss = new ServerSocket(PORT);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to open port " + PORT);
            System.exit(1);
            return;
        }

        //  Setup connection accepting thread
        new SocketAcceptor(ss, serverCallback);
    }

    public static void main(String[] args) throws InterruptedException {
        new ChatServer();

        //  run loop
        runEventLoop();
    }
}
