package org.apache.activemq.transport.stomp.legacy.frame.translator.handlers;

import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.transport.stomp.ProtocolException;
import org.apache.activemq.transport.stomp.Stomp;
import org.apache.activemq.transport.stomp.StompFrame;
import org.apache.activemq.util.ByteArrayOutputStream;

import javax.jms.JMSException;
import java.io.DataOutputStream;
import java.util.Map;

public class AMQTypeBasedFrameHandler extends AbstractFrameHandler {
    @Override
    public ActiveMQMessage handle(StompFrame command) throws JMSException, ProtocolException {
        final Map<?, ?> headers = command.getHeaders();

        if (!headers.containsKey(Stomp.Headers.AMQ_MESSAGE_TYPE)) {
            return next != null ? next.handle(command) : null;
        }

        String intendedType = (String) headers.get(Stomp.Headers.AMQ_MESSAGE_TYPE);
        if (intendedType.equalsIgnoreCase("text")) {
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
        } else if (intendedType.equalsIgnoreCase("bytes")) {
            ActiveMQBytesMessage byteMessage = new ActiveMQBytesMessage();
            byteMessage.writeBytes(command.getContent());
            return byteMessage;
        } else {
            throw new ProtocolException("Unsupported message type '" + intendedType + "'", false);
        }
    }
}
