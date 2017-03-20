package org.apache.activemq.transport.stomp.legacy.frame.translator.handlers;

import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.transport.stomp.ProtocolException;
import org.apache.activemq.transport.stomp.Stomp;
import org.apache.activemq.transport.stomp.StompFrame;

import javax.jms.JMSException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class ContentTypeBasedFrameHandler extends AbstractFrameHandler {
    @Override
    public ActiveMQMessage handle(StompFrame command) throws JMSException, ProtocolException {
        final Map<?, ?> headers = command.getHeaders();

        if (!headers.containsKey(Stomp.Headers.CONTENT_TYPE)) {
            return next != null ? next.handle(command) : null;
        }

        String contentType = (String) headers.get(Stomp.Headers.CONTENT_TYPE);

        // parse content-type := type "/" subtype *[";" parameter]
        int slashIndex = contentType.indexOf(Stomp.Headers.ContentType.TYPESUBTYPE_SEPARATOR);
        int columnIndex = contentType.indexOf(Stomp.Headers.ContentType.PARAMETER_SEPARATOR);

        if (slashIndex <= 0) {
            throw new ProtocolException("Invalid content type format: type not found in " + contentType);
        }

        if (slashIndex == contentType.length() - 1) {
            throw new ProtocolException("Invalid content type format: subtype not found in " + contentType);
        }

        if (columnIndex >= 0 && slashIndex + 1 >= columnIndex) {
            throw new ProtocolException("Invalid content type format: unexpected " + Stomp.Headers.ContentType.PARAMETER_SEPARATOR + " in " + contentType);
        }

        // parse type/subtype
        String type = contentType.substring(0, slashIndex).trim();

        // parse parameters
        HashMap<String, String> parameters = null;
        if (columnIndex > 0) {
            parameters = new HashMap<>();
            String parameterString = contentType.substring(columnIndex + 1, contentType.length());
            String[] keyValueStrings = parameterString.split(Stomp.Headers.ContentType.PARAMETER_SEPARATOR);

            for (String keyValueString : keyValueStrings) {
                String[] keyValue = keyValueString.split(Stomp.Headers.ContentType.KEYVALUE_SEPARATOR);

                if (keyValue.length != 2 || keyValue[0].length() == 0 || keyValue[1].length() == 0) {
                    throw new ProtocolException("Invalid content type format: bad parameter " + keyValueString);
                }

                parameters.put(keyValue[0], keyValue[1]);
            }

        }

        // try to convert bytes to encoded text
        String text = null;
        if (type.equals(Stomp.Headers.ContentType.TYPE_TEXT)) {
            String charset;
            if (parameters != null && parameters.containsKey(Stomp.Headers.ContentType.PARAMETER_CHARSET)) {
                charset = parameters.get(Stomp.Headers.ContentType.PARAMETER_CHARSET);
            } else {
                charset = Stomp.Headers.ContentType.PARAMETER_DEFAULT_CHARSET;
            }

            try {
                text = new String(command.getContent(), charset.toUpperCase());
            } catch (UnsupportedEncodingException e) {
                throw new ProtocolException("Invalid content type format: unsupported charset " + charset);
            }
        }

        if (text != null) {
            ActiveMQTextMessage tm = new ActiveMQTextMessage();
            tm.setText(text);
            return tm;
        } else {
            ActiveMQBytesMessage bm = new ActiveMQBytesMessage();
            bm.writeBytes(command.getContent());
            return bm;
        }
    }
}
