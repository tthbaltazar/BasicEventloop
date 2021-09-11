package com.tthbaltazar.basiceventloop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingQueue;

public class Basic {
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

    /*

    */

    /**
        Example IO task: reading lines from the console.

        From a background thread, the callback will be called from the event loop.
    */
    public static class ConsoleReader {
        public interface ConsoleReaderCallback {
            void onReadLine(ConsoleReader reader, String input);
        }

        public ConsoleReader(ConsoleReaderCallback cb) {
            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    while(true) {
                        //  Read in the background thread
                        String line = reader.readLine();

                        //  Schedule the callback
                        addTask(() -> {
                            //  This will run on the main thread
                            cb.onReadLine(ConsoleReader.this, line);
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private static int lineCount = 0;
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Hello world!");

        ConsoleReader r = new ConsoleReader((reader, s) -> {
            if (s.length() > 0) {
                lineCount++;
                System.out.println("You entered: " + s);
            } else {
                System.out.println("You entered " + lineCount + " lines before");
            }
        });

        runEventLoop();
    }
}
