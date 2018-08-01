package org.hcjf.console.shell;

import org.hcjf.console.ConsoleClient;
import org.hcjf.console.TtyListener;
import org.hcjf.io.console.ServerMetadata;
import org.hcjf.io.console.messages.EvaluateQueryableMessage;
import org.hcjf.io.console.messages.ExecuteMessage;
import org.hcjf.io.net.messages.ResponseMessage;
import org.hcjf.layers.query.Queryable;
import org.hcjf.properties.SystemProperties;
import org.hcjf.service.ServiceSession;
import org.hcjf.utils.Strings;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author javaito
 */
public abstract class Shell {

    private static final String EVALUATING_QUERY = "Evaluating query...";
    private static final String RESULT_SET_SIZE = "Result set size: %d";
    private static final String SERVER_DATA = "Protocol Version: 1.0.0 | Server: %s | Version: %s | Cluster: %s | Id: %s";

    private static final String CLEAR_COMMAND = "clear";
    private static final String SET_TIMEOUT = "setTimeout";
    private static final String EXIT_COMMAND = "exit";
    private static final String QUIT_COMMAND = "quit";

    private final TtyListener ttyListener;
    private final ServerMetadata serverMetadata;
    private final ConsoleClient consoleClient;
    private String prompt;
    private Shell openShell;
    private Long timeout;
    private DateFormat dateFormat;

    public Shell(TtyListener ttyListener, ServerMetadata serverMetadata, ConsoleClient consoleClient) {
        this.ttyListener = ttyListener;
        this.serverMetadata = serverMetadata;
        this.consoleClient = consoleClient;
        this.timeout = 10000L;
        this.dateFormat = SystemProperties.getDateFormat(SystemProperties.HCJF_DEFAULT_DATE_FORMAT);
    }

    public final void execute(Command command) throws Throwable {
        switch (command.getCommand()) {
            case CLEAR_COMMAND: {
                getTtyListener().clear();
                printHead();
                break;
            }
            case SET_TIMEOUT: {
                setTimeout((Long) command.getParameters().get(0));
                break;
            }
            case QUIT_COMMAND: case EXIT_COMMAND: {
                Shell parent = null;
                Shell currentShell = this;
                while(currentShell.getOpenShell() != null) {
                    parent = currentShell;
                    currentShell = currentShell.getOpenShell();
                }
                if(currentShell.equals(this)) {
                    System.exit(0);
                } else {
                    parent.setOpenShell(null);
                }
                break;
            }
            default: {
                if(getOpenShell() != null) {
                    getOpenShell().execute(command);
                } else {
                    delegateCommand(command);
                }
            }
        }
    }

    public abstract void delegateCommand(Command command) throws Throwable;

    /**
     *
     * @param error
     */
    public void printError(String error) {
        System.out.print(Strings.StandardOutput.RED);
        System.out.println(error);
        System.out.print(Strings.StandardOutput.RESET);
    }

    /**
     * Print the head of th console.
     */
    public void printHead() {
        getTtyListener().clear();
        System.out.printf(Strings.StandardOutput.BLUE);
        System.out.printf(SERVER_DATA,
                Objects.toString(getServerMetadata().getServerName(), Strings.EMPTY_STRING),
                Objects.toString(getServerMetadata().getServerVersion(), Strings.EMPTY_STRING),
                Objects.toString(getServerMetadata().getClusterName(), Strings.EMPTY_STRING),
                Objects.toString(getServerMetadata().getInstanceId(), Strings.EMPTY_STRING));
        System.out.printf(Strings.StandardOutput.RESET);
        System.out.printf(Strings.CARRIAGE_RETURN_AND_LINE_SEPARATOR);
        System.out.flush();
    }

    protected void printObject(Object object) {
        if(object instanceof Collection) {
            printCollection((Collection) object, 0, ((Collection) object).size());
        } else {
            System.out.println(Objects.toString(object));
        }
    }

    protected void printCollection(Collection collection, int start, int end) {
        int count = 0;
        for(Object object : collection) {
            if(count >= start && count < end) {
                System.out.print(count % 2 == 0 ? Strings.StandardOutput.BLUE_BACKGROUND : Strings.StandardOutput.YELLOW_BACKGROUND);
                if (object instanceof Map) {
                    printMap((Map) object, count % 2 == 0 ? Strings.StandardOutput.BLUE_BACKGROUND : Strings.StandardOutput.YELLOW_BACKGROUND, count+1);
                } else {
                    System.out.print(Objects.toString(object));
                }
                System.out.println(Strings.StandardOutput.RESET);
            }
            count++;
        }
    }

    private void printMap(Map map, String backgroundColor, int index) {
        System.out.print(index > 0 ? index + ": " : "");
        Object object;
        for(Object key : map.keySet()) {
            object = map.get(key);
            System.out.print(Strings.StandardOutput.BLACK_BOLD_BRIGHT);
            System.out.print(backgroundColor);
            System.out.print(Objects.toString(key));
            System.out.print(Strings.StandardOutput.WHITE);
            System.out.print(backgroundColor);
            System.out.print(Strings.OBJETC_FIELD_SEPARATOR);
            System.out.print(Objects.toString(object));
            System.out.print("  ");
        }
    }

    protected TtyListener getTtyListener() {
        return ttyListener;
    }

    public ServerMetadata getServerMetadata() {
        return serverMetadata;
    }

    public ConsoleClient getConsoleClient() {
        return consoleClient;
    }

    public String getPrompt() {
        String result;
        if(getOpenShell() != null) {
            result = prompt + "/" + getOpenShell().getPrompt();
        } else {
            result = prompt;
        }
        return result;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    protected Shell getOpenShell() {
        return openShell;
    }

    protected void setOpenShell(Shell openShell) {
        this.openShell = openShell;
    }

    protected Long getTimeout() {
        return timeout;
    }

    protected void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public DateFormat getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * This method sent a message to evaluate a query and wait the response.
     * @param queryable Queryable instance.
     * @return Returns the query response.
     */
    protected Object evaluateQueryable(Queryable queryable) throws Throwable {
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
                getConsoleClient().send(evaluateQueryableMessage);
                result.set(getConsoleClient().getResult(evaluateQueryableMessage.getId()));
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
    protected Object executeCommand(Command command) throws Throwable {
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
                getConsoleClient().send(executeMessage);
                result.set(getConsoleClient().getResult(executeMessage.getId()));
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
}
