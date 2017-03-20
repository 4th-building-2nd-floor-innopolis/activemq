package org.apache.activemq.transport.stomp.legacy.frame.translator.handlers;

import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.transport.stomp.ProtocolException;
import org.apache.activemq.transport.stomp.Stomp;
import org.apache.activemq.transport.stomp.StompFrame;

import javax.jms.JMSException;
import java.util.Map;

public class ContentLengthBasedFrameHandler extends AbstractFrameHandler {
    @Override
    public ActiveMQMessage handle(StompFrame command) throws JMSException, ProtocolException {
        final Map<?, ?> headers = command.getHeaders();

        if (!headers.containsKey(Stomp.Headers.CONTENT_LENGTH)) {
            return next != null ? next.handle(command) : null;
        }

        headers.remove(Stomp.Headers.CONTENT_LENGTH);
        ActiveMQBytesMessage bm = new ActiveMQBytesMessage();
        bm.writeBytes(command.getContent());
        return bm;
    }
}
