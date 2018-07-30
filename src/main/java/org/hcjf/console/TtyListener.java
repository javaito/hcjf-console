package org.hcjf.console;

import javafx.scene.input.KeyCode;
import org.hcjf.utils.Strings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class implements all the functions for the different inputs.
 * @author javaito
 */
class TtyListener implements Runnable {

    private static final String GET_TTY_CONFIG = "-g";
    private static final String CHARACTER_BUFFERED_COMMAND = "-icanon min 1";
    private static final String DISABLE_ECHOING_COMMAND = "-echo";
    private static final String STTY_COMMAND = "stty %s < /dev/tty";
    private static final String SECRET_CHARACTER = "*";

    private static final Long LEFT = 4479771L;
    private static final Long RIGHT = 4414235L;
    private static final Long UP = 4283163L;
    private static final Long DOWN = 4348699L;
    private static final Long DEL = 2117294875L;

    private String ttyConfig;
    private final StringBuilder line;
    private int linePosition;
    private boolean listening;
    private boolean secret;
    private final List<String> history;
    private int historyPosition;
    private String prompt;
    private String promptColor;

    public TtyListener() {
        line = new StringBuilder();
        history = new ArrayList<>();
    }

    @Override
    public void run() {
        try {

            //Config the system console, deshabling the echoing mode and setting a buffer size in 1.
            configTty();

            //Internal buffer to read from the input.
            byte[] buffer = new byte[8];
            //Long representation of the input.
            long command;
            long bufferPart;
            while (!Thread.currentThread().isInterrupted()) {

                //Read something only if the listener is blocking some thread.
                if (System.in.available() != 0 && listening) {
                    try {
                        System.in.read(buffer);

                        //Creates the long representation of the internal buffer.
                        command = 0;
                        for (int i = 0; i < 8; i++) {
                            bufferPart = buffer[i];
                            command += bufferPart << i * 8;
                        }

                        if (command == KeyCode.ENTER.getCode()) {
                            //If the command is equals to the enter code then the current editing line
                            //is complete and the thread blocking is notified.

                            if(!secret) {
                                //If the listener is not in secret mode then the line is stored into the history.
                                history.add(line.toString());
                                historyPosition = history.size() + 1;
                            }
                            synchronized (line) {
                                line.notifyAll();
                                listening = false;
                            }
                            System.out.println();
                        } else if(command == KeyCode.DELETE.getCode()) {
                            //If the command is equals to the delete code then erase the character
                            if(line.length() > 0 && linePosition > 0) {
                                System.out.print("\033[1D");
                                linePosition--;
                                line.delete(linePosition, linePosition+1);
                                printLine();
                            }
                        } else if(command == LEFT) {
                            if(linePosition > 0) {
                                System.out.print("\033[1D");
                                linePosition--;
                            }
                        } else if(command == RIGHT) {
                            if(linePosition < line.length()) {
                                System.out.print("\033[1C");
                                linePosition++;
                            }
                        } else if(command == UP) {
                            if(!history.isEmpty() && !secret) {
                                if (historyPosition > 1) {
                                    historyPosition--;
                                }
                                resetLine();
                                line.setLength(0);
                                line.append(history.get(historyPosition-1));
                                linePosition = line.length();
                                printLine();
                            }
                        } else if(command == DOWN) {
                            if(!history.isEmpty() && !secret) {
                                if(historyPosition == history.size()) {
                                    resetLine();
                                    printLine();
                                } else {
                                    if (historyPosition < history.size()) {
                                        historyPosition++;
                                    }
                                    resetLine();
                                    line.setLength(0);
                                    line.append(history.get(historyPosition - 1));
                                    linePosition = line.length();
                                    printLine();
                                }
                            }
                        } else {
                            if(command == (byte)command) {
                                line.insert(linePosition++, (char) command);
                                printLine();
                            }
                        }
                        Arrays.fill(buffer, (byte) 0);
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                    }
                }

                Thread.sleep(5);
            }
        } catch (Exception ex) {
        } finally {
            try {
                stty(ttyConfig.trim());
            }
            catch (Exception e) {
            }
        }
    }

    /**
     * Print the prompt.
     */
    private void printPrompt() {
        if(promptColor != null) {
            System.out.print(promptColor);
            System.out.print(prompt);
            System.out.print(Strings.StandardOutput.RESET);
        } else {
            System.out.print(prompt);
        }
    }

    /**
     * Print the current editing line.
     */
    private void printLine() {
        //Erases the entire current line.
        System.out.print("\033[2K");

        //Verify if the internal buffer contains something.
        if(line.length() > 0) {
            //If the internal buffer is not empty then move the cursor to the start of the screen
            //using the prompt length and the current line length, and print the prompt.
            System.out.printf("\033[%dD", prompt.length() + line.length());
            printPrompt();

            //Verify if the listener instance is the secret mode.
            if(secret) {
                //If the secret mode is available then print one secret character for
                //each char into the current editing line
                for (int i = 0; i < line.length(); i++) {
                    System.out.print(SECRET_CHARACTER);
                }
            } else {
                //If the secret mode is not available then print the current editing lien.
                System.out.print(line.toString());
            }

            //Move the cursor to the start of the screen again, then move forward until
            //the prompt length plus the line position.
            System.out.printf("\033[%dD", prompt.length() + line.length());
            if(linePosition > 0) {
                System.out.printf("\033[%dC", prompt.length() + linePosition);
            }
        } else {
            //If the internal buffer is empty then move the cursor to the start of the screen
            //using the prompt length, and print only the prompt.
            System.out.printf("\033[%dD", prompt.length());
            printPrompt();
        }
    }

    /**
     * Erase the the text line, print prompt again, and clean the internal buffer.
     */
    private void resetLine() {
        System.out.printf("\033[%dD", prompt.length() + linePosition);
        printPrompt();
        line.setLength(0);
        linePosition = 0;
    }

    /**
     * Erase the text line, and clean the internal buffer.
     */
    private void cleanLine() {
        System.out.printf("\033[%dD", prompt.length() + linePosition);
        line.setLength(0);
        linePosition = 0;
    }

    /**
     * Clean all the screen, and clean the internal buffer.
     */
    public void clear() {
        System.out.printf("\033[1J");
        System.out.printf("\033[H");
        line.setLength(0);
        linePosition = 0;
    }

    /**
     * This method block the current thread until obtain a command from the input, with the
     * secret mode available. The secret mode hide the text input using a special char '*'
     * @param prompt Text to show into the screen.
     * @param color Color of the prompt text.
     * @param arguments Arguments to replace into the prompt text.
     * @return Returns the command.
     */
    public String readSecret(String prompt, String color, String... arguments) {
        secret = true;
        String result = read(prompt, color, arguments);
        secret = false;
        return result;
    }

    /**
     * This method block the current thread until obtain a command from the input.
     * @param prompt Text to show into the screen.
     * @param color Color of the prompt text.
     * @param arguments Arguments to replace into the prompt text.
     * @return Returns the command.
     */
    public String read(String prompt, String color, String... arguments) {
        this.prompt = String.format(prompt, arguments);
        this.promptColor = color;
        String result;
        synchronized (line) {
            resetLine();
            listening = true;
            try {
                line.wait();
            } catch (InterruptedException e) {
            }
            listening = false;
            result = line.toString();
            cleanLine();
        }
        return result;
    }


    /**
     * This method set a tty configuration in order to read one character at time and
     * disabling the echo of each character.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void configTty() throws IOException, InterruptedException {
        //Getting current tty config
        ttyConfig = stty(GET_TTY_CONFIG);

        //Set the console to be character-buffered instead of line-buffered
        stty(CHARACTER_BUFFERED_COMMAND);

        //Disable character echoing
        stty(DISABLE_ECHOING_COMMAND);
    }

    /**
     * This method call the stty command with the specific arguments.
     * @param args Argument for the command.
     * @return Returns the stty command response.
     * @throws IOException
     * @throws InterruptedException
     */
    private String stty(final String args) throws IOException, InterruptedException {
        String cmd = String.format(STTY_COMMAND, args);
        return exec(new String[]{"sh", "-c", cmd});
    }

    /**
     * Execute the command and return all the output of the execution.
     * @param cmd Command to execute.
     * @return Execution output.
     * @throws IOException
     * @throws InterruptedException
     */
    private String exec(final String[] cmd) throws IOException, InterruptedException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Process process = Runtime.getRuntime().exec(cmd);
        int character;
        InputStream in = process.getInputStream();

        while ((character = in.read()) != -1) {
            buffer.write(character);
        }

        in = process.getErrorStream();

        while ((character = in.read()) != -1) {
            buffer.write(character);
        }

        process.waitFor();

        String result = new String(buffer.toByteArray());
        return result;
    }

}
