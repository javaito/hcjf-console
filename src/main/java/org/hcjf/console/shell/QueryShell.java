package org.hcjf.console.shell;

import org.hcjf.console.ConsoleClient;
import org.hcjf.console.TtyListener;
import org.hcjf.io.console.ServerMetadata;
import org.hcjf.layers.query.JoinableMap;
import org.hcjf.layers.query.Query;

import java.util.Collection;

/**
 * @author javaito
 */
public class QueryShell extends Shell {

    private static final String PROMPT_WITH_RESULT_SET = "%s[size:%d, page%d/%d]";

    private static final String NEXT = "next";
    private static final String PREVIOUS = "previous";
    private static final String PAGE = "page";
    private static final String SET_PAGE_SIZE = "setPageSize";

    private Collection<JoinableMap> resultSet;
    private Integer currentPage;
    private Integer pageSize;
    private String originalPrompt;

    public QueryShell(TtyListener ttyListener, ServerMetadata serverMetadata, ConsoleClient consoleClient) {
        super(ttyListener, serverMetadata, consoleClient);
        currentPage = 1;
        pageSize = 5;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public void delegateCommand(Command command) throws Throwable {
        switch (command.getCommand()) {
            case NEXT: {
                if(resultSet != null) {
                    if (currentPage < getMaxPage()) {
                        currentPage++;
                    }
                }
                printPage();
                break;
            }
            case PREVIOUS: {
                if(resultSet != null) {
                    if (currentPage > 1) {
                        currentPage--;
                    }
                }
                printPage();
                break;
            }
            case PAGE: {
                if(command.getParameters().size() == 1) {
                    try {
                        currentPage = ((Long) command.getParameters().get(0)).intValue();
                    } catch (Exception ex) {
                        printError("You must indicate the page number (i.e. page 1)");
                    }
                } else {
                    printError("You must indicate the page number (i.e. page 1)");
                }
                printPage();
                break;
            }
            case SET_PAGE_SIZE: {
                if(command.getParameters().size() == 1) {
                    try {
                        pageSize = (int) ((Long) command.getParameters().get(0)).intValue();
                        currentPage = 1;
                    } catch (Exception ex) {
                        printError("You must indicate the page size (i.e. page 1)");
                    }
                } else {
                    printError("You must indicate the page size (i.e. page 1)");
                }
                printPage();
                break;
            }
            default:{
                resultSet = (Collection<JoinableMap>) evaluateQueryable(Query.compile(command.getLine()));
                printPage();
            }
        }
    }

    private void printPage() {
        if(originalPrompt == null) {
            originalPrompt = getPrompt();
        }


        if(resultSet != null) {
            printCollection(resultSet, (currentPage-1) * pageSize, currentPage * pageSize);
            setPrompt(String.format(PROMPT_WITH_RESULT_SET,
                    originalPrompt, resultSet.size(), currentPage, getMaxPage()));
        } else {
            System.out.println("Make some query first");
        }
    }

    private int getMaxPage() {
        return (int) Math.ceil((resultSet.size() * 1.0) / (pageSize * 1.0));
    }

}
