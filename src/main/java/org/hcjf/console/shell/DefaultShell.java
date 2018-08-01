package org.hcjf.console.shell;

import org.hcjf.console.ConsoleClient;
import org.hcjf.console.TtyListener;
import org.hcjf.io.console.ServerMetadata;
import org.hcjf.layers.query.ParameterizedQuery;
import org.hcjf.layers.query.Query;
import org.hcjf.layers.query.Queryable;

/**
 * @author javaito
 */
public class DefaultShell extends Shell {

    private static final String EVALUATE_COMMAND = "evaluate";

    public DefaultShell(TtyListener ttyListener, ServerMetadata serverMetadata, ConsoleClient consoleClient) {
        super(ttyListener, serverMetadata, consoleClient);
    }

    @Override
    public void delegateCommand(Command command) throws Throwable {
        switch (command.getCommand()) {
            case EVALUATE_COMMAND: {
                if(command.getParameters().size() == 0) {
                    QueryShell queryShell = new QueryShell(getTtyListener(), getServerMetadata(), getConsoleClient());
                    queryShell.setPrompt("query");
                    setOpenShell(queryShell);
                } else {
                    Queryable queryable = Query.compile((String) command.getParameters().get(0));
                    if (command.getParameters().size() > 1) {
                        queryable = ((Query) queryable).getParameterizedQuery();
                        for (int i = 1; i < command.getParameters().size(); i++) {
                            ((ParameterizedQuery) queryable).add(command.getParameters().get(i));
                        }
                    }
                    Object result = evaluateQueryable(queryable);
                    printObject(result);
                }
                break;
            }
            default: {
                Object result = executeCommand(command);
                printObject(result);
            }
        }
    }
}
