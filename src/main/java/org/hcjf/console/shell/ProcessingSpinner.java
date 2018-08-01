package org.hcjf.console.shell;

import org.hcjf.console.Console;
import org.hcjf.service.Service;
import org.hcjf.service.ServiceSession;
import org.hcjf.utils.Strings;

/**
 * This class show a spinner while waiting the message response.
 */
public class ProcessingSpinner extends Thread {

    private static final String[] PROCESSING_CHARS = {"\\", "|", "/", "-"};
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
            System.out.printf("%s %s %d ms ", message, PROCESSING_CHARS[i], System.currentTimeMillis() - time);
            if(done){
                switch (endMessage) {
                    case FAIL: {
                        System.out.printf(Strings.StandardOutput.RED);
                        break;
                    }
                    case DONE: {
                        System.out.printf(Strings.StandardOutput.GREEN);
                        break;
                    }
                }
                break;
            }
            if((System.currentTimeMillis() - time) >= timeout){
                System.out.printf(Strings.StandardOutput.CYAN);
                endMessage = TIMEOUT;
                result = Strings.EMPTY_STRING;
                break;
            }
            System.out.printf(Strings.CARRIAGE_RETURN);
            i = (i+1)%4;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }
        System.out.printf("\r[%s %dms] %s%s                         " +
                "\r\n", endMessage, System.currentTimeMillis() - time, result, Strings.StandardOutput.RESET);
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

    public interface Consumer {

        String consume(String... values);

    }
}
