package com.jflop.load;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * TODO: Document!
 *
 * @author artem on 11/01/2017.
 */
public class LoadProcess {

    private static final Logger logger = Logger.getLogger(LoadProcess.class.getName());

    static Process start(String agentPath) {
        try {
            Process process = new ProcessBuilder().command(
                    "java", "-javaagent:" + agentPath,
                    "-classpath", System.getProperty("java.class.path"),
                    LoadProcess.class.getName())
                    .inheritIO()
                    .start();
            return process;
        } catch (IOException e) {
            throw new RuntimeException("Failed starting the process", e);
        }
    }

    public static void main(String[] args) {
        logger.info("Client process begin");
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        logger.info("Client process end");
    }
}
