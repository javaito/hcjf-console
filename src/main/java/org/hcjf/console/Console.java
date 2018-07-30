package org.hcjf.console;

import org.hcjf.io.console.ServerMetadata;
import org.hcjf.io.console.SessionMetadata;
import org.hcjf.io.console.messages.EvaluateQueryableMessage;
import org.hcjf.io.console.messages.ExecuteMessage;
import org.hcjf.io.console.messages.GetMetadataMessage;
import org.hcjf.io.console.messages.LoginMessage;
import org.hcjf.io.net.NetService;
import org.hcjf.io.net.messages.ResponseMessage;
import org.hcjf.layers.query.ParameterizedQuery;
import org.hcjf.layers.query.Query;
import org.hcjf.layers.query.Queryable;
import org.hcjf.properties.SystemProperties;
import org.hcjf.service.Service;
import org.hcjf.service.ServiceSession;
import org.hcjf.utils.Strings;

import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class implements a console to use as client connected with some instance of hcjf.
 * @author javaito
 */
public class Console {

    private static final String[] PROCESSING_CHARS = {"\\", "|", "/", "-"};
    private static final String EVALUATING_QUERY = "Evaluating query...";
    private static final String RESULT_SET_SIZE = "Result set size: %d";
    private static final String TRYING_WITH = "Trying with %s:%d \n";
    private static final String CONNECTING = "Connecting...";
    private static final String UNABLE_TO_CONNECT = "Unable to connect";
    private static final String CONNECTED = "Connected";
    private static final String CONNECTION_LOST = "\r\nConnection lost %s:%d\r\n";
    private static final String PROMPT = "%s$%s ";
    private static final String SERVER_DATA = "Protocol Version: 1.0.0 | Server: %s | Version: %s | Cluster: %s | Id: %s";
    private static final String READ_FIELD = "%s: ";
    private static final String LOGIN_FAIL = "Login fail";

    private static final String CLEAR_COMMAND = "clear";
    private static final String SET_TIMEOUT = "setTimeout";
    private static final String EVALUATE_COMMAND = "evaluate";
    private static final String EXIT_COMMAND = "exit";
    private static final String QUIT_COMMAND = "quit";

    private final String host;
    private final Integer port;
    private Long timeout;
    private String prompt;
    private ConsoleClient consoleClient;
    private TtyListener ttyListener;
    private ServerMetadata metadata;
    private DateFormat dateFormat;

    public Console(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.timeout = 10000L;
        this.dateFormat = SystemProperties.getDateFormat(SystemProperties.HCJF_DEFAULT_DATE_FORMAT);
        this.ttyListener = new TtyListener();
    }

    /**
     * Returns the timeout of the commands.
     * @return Timeout.
     */
    public Long getTimeout() {
        return timeout;
    }

    /**
     * Set the timeout of the command.
     * @param timeout Timeout.
     */
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns the text to show as prompt.
     * @return Text to show as prompt.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Set the text to show as prompt.
     * @param prompt Text to show as prompt.
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * Returns the date format associated to the console.
     * @return Date format.
     */
    public DateFormat getDateFormat() {
        return dateFormat;
    }

    /**
     * Set the date format associated to the console.
     * @param dateFormat Date format.
     */
    public void setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    public void init() {
        Service.run(ttyListener, ServiceSession.getGuestSession());

        Service.run(() -> {
            consoleClient = new ConsoleClient(host, port);
            System.out.printf(TRYING_WITH, host, port);
            ProcessingSpinner processingSpinner = new ProcessingSpinner(CONNECTING, timeout);
            processingSpinner.start();
            processingSpinner.consume((C)->{
                try {
                    NetService.getInstance().registerConsumer(consoleClient);
                    consoleClient.waitForConnect();
                    if (!consoleClient.isConnected()) {
                        throw new RuntimeException(UNABLE_TO_CONNECT);
                    }
                    metadata = getMetadata();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                return CONNECTED;
            });
            try {
                processingSpinner.join();
            } catch (InterruptedException e) {
            }

            if (!consoleClient.isConnected() || metadata == null) {
                System.exit(1);
            }

            try {
                if (metadata.getLoginRequired()) {
                    login();
                }
            } catch (Throwable ex) {
                System.out.printf(LOGIN_FAIL);
            }

            try {
                ttyListener.clear();
                printHead();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                System.exit(1);
            }
            while(!Thread.currentThread().isInterrupted()) {
                if (!consoleClient.isConnected()) {
                    System.out.printf(CONNECTION_LOST, host, port);
                    System.exit(1);
                }
                String line = ttyListener.read(PROMPT, Strings.StandardOutput.YELLOW,
                        ServiceSession.getCurrentIdentity().getSessionName(),
                        prompt);

                if(line.isEmpty()) {
                    continue;
                }
                try {
                    Command command = new Command(line, getDateFormat());
                    switch (command.getCommand()) {
                        case CLEAR_COMMAND: {
                            ttyListener.clear();
                            printHead();
                            break;
                        }
                        case SET_TIMEOUT: {
                            setTimeout((Long) command.getParameters().get(0));
                            break;
                        }
                        case EVALUATE_COMMAND: {
                            Queryable queryable = Query.compile((String) command.getParameters().get(0));
                            if(command.getParameters().size() > 1) {
                                queryable = ((Query)queryable).getParameterizedQuery();
                                for (int i = 1; i < command.parameters.size(); i++) {
                                    ((ParameterizedQuery) queryable).add(command.getParameters().get(i));
                                }
                            }
                            Object result = evaluateQueryable(queryable);
                            if(result != null) {
                                System.out.printf(result.toString());
                                System.out.printf(Strings.CARRIAGE_RETURN_AND_LINE_SEPARATOR);
                                System.out.flush();
                            }
                            break;
                        }
                        case QUIT_COMMAND: case EXIT_COMMAND: {
                            System.exit(0);
                        }
                        default: {
                            Object result = executeCommand(command);
                            if(result != null) {
                                System.out.printf(result.toString());
                                System.out.printf(Strings.CARRIAGE_RETURN_AND_LINE_SEPARATOR);
                                System.out.flush();
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    System.out.printf(Strings.StandardOutput.RED);
                    System.out.flush();
                    throwable.printStackTrace(System.out);
                    System.out.flush();
                    System.out.printf(Strings.StandardOutput.RESET);
                    System.out.flush();
                }
            }
        }, ServiceSession.getGuestSession());
    }

    /**
     * Print the head of th console.
     */
    private void printHead() {
        System.console().printf(Strings.StandardOutput.BLUE);
        System.out.printf(SERVER_DATA,
                Objects.toString(metadata.getServerName(), Strings.EMPTY_STRING),
                Objects.toString(metadata.getServerVersion(), Strings.EMPTY_STRING),
                Objects.toString(metadata.getClusterName(), Strings.EMPTY_STRING),
                Objects.toString(metadata.getInstanceId(), Strings.EMPTY_STRING));
        System.console().printf(Strings.StandardOutput.RESET);
        System.out.printf(Strings.CARRIAGE_RETURN_AND_LINE_SEPARATOR);
        System.out.flush();
    }

    /**
     * Make a login sending a message to the server.
     * @return Returns the session instance.
     * @throws Throwable
     */
    private SessionMetadata login() throws Throwable {
        System.out.printf(Strings.CARRIAGE_RETURN_AND_LINE_SEPARATOR);
        System.out.flush();
        Map<String,Object> parameters = new HashMap<>();
        for(String field : metadata.getLoginFields()) {
            parameters.put(field, ttyListener.read(READ_FIELD, null, field));
        }
        for(String field : metadata.getLoginSecretFields()) {
            parameters.put(field, new String(ttyListener.readSecret(READ_FIELD, null, field)));
        }
        LoginMessage loginMessage = new LoginMessage();
        loginMessage.setId(UUID.randomUUID());
        loginMessage.setParameters(parameters);
        consoleClient.send(loginMessage);
        ResponseMessage responseMessage = consoleClient.getResult(loginMessage.getId());
        if(responseMessage.getThrowable() != null) {
            throw responseMessage.getThrowable();
        }
        SessionMetadata sessionMetadata = (SessionMetadata) responseMessage.getValue();
        ServiceSession serviceSession = new ServiceSession(sessionMetadata.getId());
        serviceSession.setSessionName(sessionMetadata.getSessionName());
        ServiceSession.getCurrentIdentity().addIdentity(serviceSession);

        System.out.println(sessionMetadata.getId());
        System.out.println(sessionMetadata.getSessionName());

        return sessionMetadata;
    }

    /**
     * This method returns the metadata of the server.
     * @return Metadata instance.
     * @throws Throwable
     */
    private ServerMetadata getMetadata() throws Throwable {
        GetMetadataMessage getMetadataMessage = new GetMetadataMessage();
        getMetadataMessage.setId(UUID.randomUUID());
        consoleClient.send(getMetadataMessage);
        return (ServerMetadata) consoleClient.getResult(getMetadataMessage.getId()).getValue();
    }

    /**
     * This method sent a message to evaluate a query and wait the response.
     * @param queryable Queryable instance.
     * @return Returns the query response.
     */
    private Object evaluateQueryable(Queryable queryable) throws Throwable {
        Object value = null;
        EvaluateQueryableMessage evaluateQueryableMessage = new EvaluateQueryableMessage();
        evaluateQueryableMessage.setId(UUID.randomUUID());
        evaluateQueryableMessage.setTimestamp(System.currentTimeMillis());
        evaluateQueryableMessage.setQueryable(queryable);
        evaluateQueryableMessage.setSessionId(ServiceSession.getSystemSession().getId());

        AtomicReference<ResponseMessage> result = new AtomicReference<>();
        ProcessingSpinner processingSppiner = new ProcessingSpinner(EVALUATING_QUERY, getTimeout());
        processingSppiner.start();
        processingSppiner.consume((C) -> {
            try {
                consoleClient.send(evaluateQueryableMessage);
                result.set(consoleClient.getResult(evaluateQueryableMessage.getId()));
                if(result.get().getThrowable() != null) {
                    throw new RuntimeException(result.get().getThrowable().getMessage(), result.get().getThrowable());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Collection collection = (Collection) result.get().getValue();
            return String.format(RESULT_SET_SIZE, collection.size());
        });
        try {
            processingSppiner.join();
        } catch (InterruptedException e) {
        }
        if(result.get() != null) {
            ResponseMessage responseMessage = result.get();
            if (responseMessage.getThrowable() != null) {
                throw responseMessage.getThrowable();
            }
            value = responseMessage.getValue();
        }
        return value;
    }

    /**
     *
     * @param command
     * @return
     * @throws Throwable
     */
    private Object executeCommand(Command command) throws Throwable {
        Object value = null;
        ExecuteMessage executeMessage = new ExecuteMessage();
        executeMessage.setId(UUID.randomUUID());
        executeMessage.setSessionId(ServiceSession.getCurrentIdentity().getId());
        executeMessage.setCommandName(command.getCommand());
        executeMessage.setParameters(command.getParameters());
        AtomicReference<ResponseMessage> result = new AtomicReference<>();
        ProcessingSpinner processingSpinner = new ProcessingSpinner(EVALUATING_QUERY, getTimeout());
        processingSpinner.start();
        processingSpinner.consume((C) -> {
            try {
                consoleClient.send(executeMessage);
                result.set(consoleClient.getResult(executeMessage.getId()));
                if(result.get().getThrowable() != null) {
                    throw new RuntimeException(result.get().getThrowable().getMessage(), result.get().getThrowable());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return Strings.EMPTY_STRING;
        });
        try {
            processingSpinner.join();
        } catch (InterruptedException e) {
        }
        if(result.get() != null) {
            ResponseMessage responseMessage = result.get();
            if (responseMessage.getThrowable() != null) {
                throw responseMessage.getThrowable();
            }
            value = responseMessage.getValue();
        }
        return value;
    }

    private static class Command {

        private String command;
        private List<Object> parameters;

        public Command(String line, DateFormat dateFormat) {
            List<String> richTexts = Strings.groupRichText(line.trim());
            String newLine = richTexts.get(richTexts.size() - 1);
            String[] parts = newLine.split(Strings.WHITE_SPACE);
            command = parts[0];
            parameters = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                String trimmedPart = parts[i].trim();
                if(trimmedPart.equals("true")) {
                    parameters.add(Boolean.TRUE);
                } else if(trimmedPart.equals("false")) {
                    parameters.add(Boolean.FALSE);
                } else if(trimmedPart.equals("null")) {
                    parameters.add(null);
                } else if(trimmedPart.startsWith(Strings.RICH_TEXT_SEPARATOR)) {
                    trimmedPart = trimmedPart.substring(1, trimmedPart.length()-1);
                    trimmedPart = richTexts.get(Integer.parseInt(trimmedPart.replace(Strings.REPLACEABLE_RICH_TEXT, Strings.EMPTY_STRING)));
                    trimmedPart = trimmedPart.replace(
                            Strings.RICH_TEXT_SKIP_CHARACTER + Strings.RICH_TEXT_SEPARATOR,
                            Strings.RICH_TEXT_SEPARATOR);
                    try {
                        parameters.add(dateFormat.parse(trimmedPart));
                    } catch (Exception ex){
                        parameters.add(trimmedPart);
                    }
                } else if(trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_UUID_REGEX))){
                    parameters.add(UUID.fromString(trimmedPart));
                } else if(trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_INTEGER_NUMBER_REGEX))){
                    parameters.add(Long.parseLong(trimmedPart));
                } else if(trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_DECIMAL_NUMBER_REGEX))) {
                    parameters.add(Double.parseDouble(trimmedPart));
                } else {
                    parameters.add(trimmedPart);
                }
            }
        }

        public String getCommand() {
            return command;
        }

        public List<Object> getParameters() {
            return parameters;
        }
    }

    /**
     * This class show a spinner while waiting the message response.
     */
    private static class ProcessingSpinner extends Thread {

        private static final String TIMEOUT = "Timeout";
        private static final String FAIL = "Fail";
        private static final String DONE = "Done";

        private final String message;
        private final Long timeout;
        private String endMessage;
        private String result;
        private boolean done;

        public ProcessingSpinner(String message, Long timeout) {
            this.message = message;
            this.timeout = timeout;
            this.done = false;
        }

        @Override
        public void run() {
            int i = 0;
            long time = System.currentTimeMillis();
            while(!isInterrupted()) {
                System.console().printf("%s %s %d ms ", message, PROCESSING_CHARS[i], System.currentTimeMillis() - time);
                System.console().flush();
                if(done){
                    switch (endMessage) {
                        case FAIL: {
                            System.console().printf(Strings.StandardOutput.RED);
                            break;
                        }
                        case DONE: {
                            System.console().printf(Strings.StandardOutput.GREEN);
                            break;
                        }
                    }
                    break;
                }
                if((System.currentTimeMillis() - time) >= timeout){
                    System.console().printf(Strings.StandardOutput.CYAN);
                    endMessage = TIMEOUT;
                    result = Strings.EMPTY_STRING;
                    break;
                }
                System.console().printf(Strings.CARRIAGE_RETURN);
                System.console().flush();
                i = (i+1)%4;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
            System.console().printf("\r[%s %dms] %s%s                         " +
                    "\r\n", endMessage, System.currentTimeMillis() - time, result, Strings.StandardOutput.RESET);
            System.console().flush();
        }

        public void consume(Consumer consumer, String... values) {
            Service.run(() -> {
                try {
                    result = consumer.consume(values);
                    endMessage = DONE;
                } catch (Exception ex) {
                    endMessage = FAIL;
                    result = ex.getMessage();
                }
                done = true;
            }, ServiceSession.getCurrentIdentity());
        }
    }

    interface Consumer {

        String consume(String... values);

    }
}
