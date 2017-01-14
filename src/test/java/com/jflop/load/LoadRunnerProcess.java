package com.jflop.load;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jflop.server.persistency.ValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standalone process with the load runner.
 * The process is started with a specific agent jar, and can be controlled remotely via the proxy.
 *
 * @author artem on 11/01/2017.
 */
public class LoadRunnerProcess {

    private static final Logger logger = Logger.getLogger(LoadRunnerProcess.class.getName());
    private static ObjectMapper mapper = new ObjectMapper();

    private static final String COMMAND_SEPARATOR = "<-name|value->";
    private static final String FLOW_SEPARATOR = "|";
    private static final String EXIT = "exit";
    private static final String SET_FLOWS = "flows";
    private static final String START_LOAD = "start-load";
    private static final String STOP_LOAD = "stop-load";

    private LoadRunner loadRunner;

    public static Proxy start(String agentPath) throws IOException {
        Process process = new ProcessBuilder().command("java", "-javaagent:" + agentPath,
                "-classpath", System.getProperty("java.class.path"), LoadRunnerProcess.class.getName())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start();
        return new Proxy(process);
    }

    public static void main(String[] args) {
        new LoadRunnerProcess().go();
    }

    private void go() {
        logger.info("Client process begin");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        boolean exit = false;
        while (!exit) {
            try {
                ValuePair<String, String> nameValue = readNextCommand(in);
                String commandName = nameValue.value1;
                writeCommandOutput(commandName, processCommand(commandName, nameValue.value2));
                exit = commandName.equals(EXIT);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read from input, ignore", e);
            }
        }
        logger.info("Client process end");
    }

    private void writeCommandOutput(String name, String returnValue) {
        System.out.println(name + COMMAND_SEPARATOR + returnValue);
    }

    private String processCommand(String name, String value) {
        switch (name) {
            case EXIT:
                return "OK";

            case SET_FLOWS:
                if (loadRunner != null && loadRunner.isRunning()) return "Illegal state: load is running.";

                List<Object[]> flowsThroughput = new ArrayList<>();
                StringTokenizer tokenizer = new StringTokenizer(value, FLOW_SEPARATOR);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    int flowEnd = token.lastIndexOf("}") + 1;
                    String flowStr = token.substring(0, flowEnd);
                    GeneratedFlow flow = GeneratedFlow.fromString(flowStr);
                    String throughputStr = token.substring(flowEnd);
                    float throughput = Float.parseFloat(throughputStr);
                    flowsThroughput.add(new Object[]{flow, throughput});
                }
                loadRunner = new LoadRunner(flowsThroughput.toArray(new Object[flowsThroughput.size()][]));
                return "OK";

            case START_LOAD:
                if (loadRunner == null || loadRunner.isRunning())
                    return "Illegal state: load " + (loadRunner == null ? "runner not set" : "is running");
                loadRunner.startLoad();
                return "OK";

            case STOP_LOAD:
                if (loadRunner == null || !loadRunner.isRunning())
                    return "Illegal state: load " + (loadRunner == null ? "runner not set" : "is not running");
                Map<String, Object[]> res = loadRunner.stopLoad(Integer.parseInt(value));
                try {
                    return mapper.writeValueAsString(res);
                } catch (JsonProcessingException e) {
                    String msg = "Failed writing load result";
                    logger.log(Level.SEVERE, msg, e);
                    return msg;
                }

            default:
                return "command not recognized";
        }
    }

    private ValuePair<String, String> readNextCommand(BufferedReader in) throws IOException {
        ValuePair<String, String> res = null;
        while (res == null)
            res = parseCommand(in.readLine());
        return res;
    }

    private static ValuePair<String, String> parseCommand(String line) {
        int pos = line.indexOf(COMMAND_SEPARATOR);
        if (pos > 0) {
            String name = line.substring(0, pos);
            String value = line.substring(pos + COMMAND_SEPARATOR.length());
            return new ValuePair<>(name, value);
        }
        return null;
    }

    static class Proxy {

        private Process process;
        private PrintStream out;

        private Scanner in;
        private Thread scannerThread;
        private boolean stopScanning;
        private Map<String, String> commandsOutput = new ConcurrentHashMap<>();

        public Proxy(Process process) {
            this.process = process;
            this.out = new PrintStream(process.getOutputStream());
            this.in = new Scanner(process.getInputStream());
            this.scannerThread = new Thread(() -> {
                while (!stopScanning && in.hasNextLine()) {
                    String line = in.nextLine();
                    ValuePair<String, String> pair = parseCommand(line);
                    if (pair == null)
                        System.out.println(line);
                    else {
                        System.out.println();
                        commandsOutput.put(pair.value1, pair.value2);
                    }
                }
            });
            scannerThread.start();
        }

        public boolean exit(int timeoutSec) {
            String response = sendCommand(EXIT, "", timeoutSec * 1000 / 2);
            if (response != null) {
                // wait a bit to let the process end
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            if (!process.isAlive()) return true;

            process.destroyForcibly();
            try {
                process.waitFor(timeoutSec, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore
            }

            stopScanning = true;
            scannerThread.interrupt();
            try {
                scannerThread.join(timeoutSec * 1000 / 5);
            } catch (InterruptedException e) {
                // ignore
            }
            return !process.isAlive();
        }

        public boolean setFlows(Object[][] flowsAndThroughput) {
            String value = "";
            for (Object[] pair : flowsAndThroughput) {
                GeneratedFlow flow = (GeneratedFlow) pair[0];
                float throughput = (float) pair[1];
                if (!value.isEmpty()) value += FLOW_SEPARATOR;
                value += flow.toString() + String.valueOf(throughput);
            }
            String res = sendCommand(SET_FLOWS, value, 1000);
            return res.equals("OK");
        }

        public boolean startLoad() {
            String res = sendCommand(START_LOAD, "", 100);
            return res.equals("OK");
        }

        public Map<String, List<Object>> stopLoad(int timeoutSec) {
            String res = sendCommand(STOP_LOAD, String.valueOf(timeoutSec), 3000);
            try {
                return mapper.readValue(res, Map.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private String sendCommand(String name, String value, int timeoutMillis) {
            out.println(name + COMMAND_SEPARATOR + value);
            out.flush();

            long border = System.currentTimeMillis() + timeoutMillis;
            String res = null;
            while (res == null && System.currentTimeMillis() < border) {
                res = commandsOutput.remove(name);
            }
            if (res == null)
                logger.severe("Got no response for command " + name + " in " + timeoutMillis + " ms.");

            logger.fine("Command " + name + " returned " + res);
            return res;
        }
    }
}
