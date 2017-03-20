package org.apache.activemq.transport.stomp.legacy.frame.translator.handlers;

import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.transport.stomp.ProtocolException;
import org.apache.activemq.transport.stomp.StompFrame;
import org.apache.activemq.util.ByteArrayOutputStream;

import java.io.DataOutputStream;

public class DefaultFrameHandler extends AbstractFrameHandler {
    @Override
    public ActiveMQMessage handle(StompFrame command) throws ProtocolException {
        ActiveMQTextMessage text = new ActiveMQTextMessage();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(command.getContent().length + 4);
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeInt(command.getContent().length);
            data.write(command.getContent());
            text.setContent(bytes.toByteSequence());
            data.close();
        } catch (Throwable e) {
            throw new ProtocolException("Text could not bet set: " + e, false, e);
        }

        return text;
    }
}
