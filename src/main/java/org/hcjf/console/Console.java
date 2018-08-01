package org.hcjf.console;

import org.hcjf.console.shell.Command;
import org.hcjf.console.shell.DefaultShell;
import org.hcjf.console.shell.ProcessingSpinner;
import org.hcjf.console.shell.Shell;
import org.hcjf.io.console.ServerMetadata;
import org.hcjf.io.console.SessionMetadata;
import org.hcjf.io.console.messages.GetMetadataMessage;
import org.hcjf.io.console.messages.LoginMessage;
import org.hcjf.io.net.NetService;
import org.hcjf.io.net.messages.ResponseMessage;
import org.hcjf.properties.SystemProperties;
import org.hcjf.service.Service;
import org.hcjf.service.ServiceSession;
import org.hcjf.utils.Cryptography;
import org.hcjf.utils.Strings;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This class implements a console to use as client connected with some instance of hcjf.
 * @author javaito
 */
public class Console {

    private static final String TRYING_WITH = "Trying with %s:%d \n";
    private static final String CONNECTING = "Connecting...";
    private static final String UNABLE_TO_CONNECT = "Unable to connect";
    private static final String CONNECTED = "Connected";
    private static final String CONNECTION_LOST = "\r\nConnection lost %s:%d\r\n";
    private static final String PROMPT = "%s$%s ";
    private static final String READ_FIELD = "%s: ";
    private static final String LOGIN_FAIL = "Login fail";

    private final String host;
    private final Integer port;
    private Long timeout;
    private String prompt;
    private ConsoleClient consoleClient;
    private TtyListener ttyListener;
    private ServerMetadata metadata;
    private DateFormat dateFormat;
    private Shell shell;

    public Console(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.timeout = 120000L;
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
            Cryptography cryptography = new Cryptography();
            consoleClient = new ConsoleClient(host, port, cryptography);
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
                    e.printStackTrace();
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

            shell = new DefaultShell(ttyListener, metadata, consoleClient);
            shell.printHead();
            shell.setPrompt(prompt);
            while(!Thread.currentThread().isInterrupted()) {
                if (!consoleClient.isConnected()) {
                    System.out.printf(CONNECTION_LOST, host, port);
                    System.exit(1);
                }
                String line = ttyListener.read(PROMPT, Strings.StandardOutput.YELLOW,
                        ServiceSession.getCurrentIdentity().getSessionName(),
                        shell.getPrompt());

                if(line.isEmpty()) {
                    continue;
                }
                try {
                    Command command = new Command(line, getDateFormat());
                    shell.execute(command);
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

}
