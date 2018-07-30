package org.hcjf.console;

import org.hcjf.utils.Strings;

/**
 * @author javaito
 */
public class Main {

    public static void main(String[] args) {

        if(args.length != 2) {
            System.out.print(Strings.StandardOutput.RED);
            System.out.println("You must indicate the host and port to connect the console: java -jar hcjf-client localhost 5900");
            System.out.print(Strings.StandardOutput.RESET);
            System.exit(1);
        }

        String host;
        Integer port;
        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } catch (Exception ex){
            System.out.print(Strings.StandardOutput.RED);
            System.out.println("Fail parsing arguments");
            System.out.print(Strings.StandardOutput.RESET);
            System.exit(1);
            return;
        }

        Console console = new Console(host, port);
        console.setPrompt(":");
        console.init();
    }

}
