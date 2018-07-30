package org.hcjf.console;

import org.hcjf.io.console.ConsoleSession;
import org.hcjf.io.net.NetPackage;
import org.hcjf.io.net.NetSession;
import org.hcjf.io.net.messages.Message;
import org.hcjf.io.net.messages.MessageBuffer;
import org.hcjf.io.net.messages.MessagesNode;
import org.hcjf.io.net.messages.ResponseMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This class implements a clients using the message protocol to connect the console with som server.
 * @author javaito
 */
class ConsoleClient extends MessagesNode<ConsoleSession> {

    private final ConsoleSession consoleSession;
    private final Map<UUID, ResponseMessage> responseMessageMap;

    public ConsoleClient(String host, Integer port) {
        super(host, port);
        consoleSession = new ConsoleSession(UUID.randomUUID(), this);
        responseMessageMap = new HashMap<>();
    }

    @Override
    protected void onRead(ConsoleSession session, Message incomingMessage) {
        synchronized (responseMessageMap) {
            if (incomingMessage instanceof ResponseMessage) {
                responseMessageMap.put(incomingMessage.getId(), (ResponseMessage) incomingMessage);
                responseMessageMap.notifyAll();
            }
        }
    }

    @Override
    public ConsoleSession getSession() {
        return consoleSession;
    }

    @Override
    public void destroySession(NetSession session) {
    }

    @Override
    public ConsoleSession checkSession(ConsoleSession session, MessageBuffer payLoad, NetPackage netPackage) {
        session.setChecked(true);
        return session;
    }

    public ResponseMessage getResult(UUID messageId) {
        ResponseMessage result = null;
        synchronized (responseMessageMap) {
            while (!Thread.currentThread().isInterrupted()) {
                if (responseMessageMap.containsKey(messageId)) {
                    result = responseMessageMap.remove(messageId);
                    break;
                } else {
                    try {
                        responseMessageMap.wait(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        return result;
    }
}
