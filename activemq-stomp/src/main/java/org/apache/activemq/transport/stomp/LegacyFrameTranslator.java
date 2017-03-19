/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.stomp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.util.ByteArrayOutputStream;
import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.util.StringArrayConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements ActiveMQ 4.0 translations
 */
public class LegacyFrameTranslator implements FrameTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyFrameTranslator.class);

    @Override
    public ActiveMQMessage convertFrame(ProtocolConverter converter, StompFrame command) throws JMSException, ProtocolException {
        final Map<?, ?> headers = command.getHeaders();
        final ActiveMQMessage msg;
        /*
         * To reduce the complexity of this method perhaps a Chain of Responsibility
         * would be a better implementation
         */
        if (headers.containsKey(Stomp.Headers.AMQ_MESSAGE_TYPE)) {
            String intendedType = (String)headers.get(Stomp.Headers.AMQ_MESSAGE_TYPE);
            if(intendedType.equalsIgnoreCase("text")){
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
                msg = text;
            } else if(intendedType.equalsIgnoreCase("bytes")) {
                ActiveMQBytesMessage byteMessage = new ActiveMQBytesMessage();
                byteMessage.writeBytes(command.getContent());
                msg = byteMessage;
            } else {
                throw new ProtocolException("Unsupported message type '"+intendedType+"'",false);
            }
        }else if(headers.containsKey(Stomp.Headers.CONTENT_TYPE)) {
            String contentType = (String)headers.get(Stomp.Headers.CONTENT_TYPE);

            // parse content-type := type "/" subtype *[";" parameter]
            int slashIndex = contentType.indexOf(Stomp.Headers.ContentType.TYPESUBTYPE_SEPARATOR);
            int columnIndex = contentType.indexOf(Stomp.Headers.ContentType.PARAMETER_SEPARATOR);

            if (slashIndex <= 0) {
                throw new ProtocolException("Invalid content type format: type not found in + " + contentType);
            }

            if (slashIndex + 1 >= contentType.length() || (columnIndex > 0 && slashIndex + 1 >= columnIndex)) {
                throw new ProtocolException("Invalid content type format: subtype not found in + " + contentType);
            }

            // parse type/subtype
            String type = contentType.substring(0, slashIndex).trim();

            // parse parameters
            HashMap<String, String> parameters = null;
            if (columnIndex > 0) {
                parameters = new HashMap<>();
                String parameterString = contentType.substring(columnIndex + 1, contentType.length());
                String[] keyValueStrings =  parameterString.split(Stomp.Headers.ContentType.PARAMETER_SEPARATOR);

                for (String keyValueString : keyValueStrings) {
                    String[] keyValue = keyValueString.split(Stomp.Headers.ContentType.KEYVALUE_SEPARATOR);

                    if (keyValue.length != 2 || keyValue[0].length() == 0 || keyValue[1].length() == 0) {
                        throw new ProtocolException("Invalid parameter format: " + keyValueString);
                    }

                    parameters.put(keyValue[0], keyValue[1]);
                }

            }

            // try to convert bytes to encoded text
            String text = null;
            if (type.equals(Stomp.Headers.ContentType.TYPE_TEXT)) {
                String charset = null;
                if (parameters != null && parameters.containsKey(Stomp.Headers.ContentType.PARAMETER_CHARSET)) {
                    charset = parameters.get(Stomp.Headers.ContentType.PARAMETER_CHARSET);
                }else {
                    charset = Stomp.Headers.ContentType.PARAMETER_DEFAULT_CHARSET;
                }

                try {
                    text = new String(command.getContent(), charset.toUpperCase());
                } catch (UnsupportedEncodingException e) {
                    // encoding is not supported, so continue with bytes
                }
            }

            if (text != null) {
                ActiveMQTextMessage tm = new ActiveMQTextMessage();
                tm.setText(text);
                msg = tm;
            }else {
                ActiveMQBytesMessage bm = new ActiveMQBytesMessage();
                bm.writeBytes(command.getContent());
                msg = bm;
            }

        }else if (headers.containsKey(Stomp.Headers.CONTENT_LENGTH)) {
            headers.remove(Stomp.Headers.CONTENT_LENGTH);
            ActiveMQBytesMessage bm = new ActiveMQBytesMessage();
            bm.writeBytes(command.getContent());
            msg = bm;
        } else {
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
            msg = text;
        }
        FrameTranslator.Helper.copyStandardHeadersFromFrameToMessage(converter, command, msg, this);
        return msg;
    }

    @Override
    public StompFrame convertMessage(ProtocolConverter converter, ActiveMQMessage message) throws IOException, JMSException {
        StompFrame command = new StompFrame();
        command.setAction(Stomp.Responses.MESSAGE);
        Map<String, String> headers = new HashMap<String, String>(25);
        command.setHeaders(headers);

        FrameTranslator.Helper.copyStandardHeadersFromMessageToFrame(converter, message, command, this);

        if (message.getDataStructureType() == ActiveMQTextMessage.DATA_STRUCTURE_TYPE) {

            if (!message.isCompressed() && message.getContent() != null) {
                ByteSequence msgContent = message.getContent();
                if (msgContent.getLength() > 4) {
                    byte[] content = new byte[msgContent.getLength() - 4];
                    System.arraycopy(msgContent.data, 4, content, 0, content.length);
                    command.setContent(content);
                }
            } else {
                ActiveMQTextMessage msg = (ActiveMQTextMessage)message.copy();
                String messageText = msg.getText();
                if (messageText != null) {
                    command.setContent(msg.getText().getBytes("UTF-8"));
                }
            }

        } else if (message.getDataStructureType() == ActiveMQBytesMessage.DATA_STRUCTURE_TYPE) {

            ActiveMQBytesMessage msg = (ActiveMQBytesMessage)message.copy();
            msg.setReadOnlyBody(true);
            byte[] data = new byte[(int)msg.getBodyLength()];
            msg.readBytes(data);

            headers.put(Stomp.Headers.CONTENT_LENGTH, Integer.toString(data.length));
            command.setContent(data);
        }

        return command;
    }

    @Override
    public String convertDestination(ProtocolConverter converter, Destination d) {
        if (d == null) {
            return null;
        }
        ActiveMQDestination activeMQDestination = (ActiveMQDestination)d;
        String physicalName = activeMQDestination.getPhysicalName();

        String rc = converter.getCreatedTempDestinationName(activeMQDestination);
        if( rc!=null ) {
            return rc;
        }

        StringBuilder buffer = new StringBuilder();
        if (activeMQDestination.isQueue()) {
            if (activeMQDestination.isTemporary()) {
                buffer.append("/remote-temp-queue/");
            } else {
                buffer.append("/queue/");
            }
        } else {
            if (activeMQDestination.isTemporary()) {
                buffer.append("/remote-temp-topic/");
            } else {
                buffer.append("/topic/");
            }
        }
        buffer.append(physicalName);
        return buffer.toString();
    }

    @Override
    public ActiveMQDestination convertDestination(ProtocolConverter converter, String name, boolean forceFallback) throws ProtocolException {
        if (name == null) {
            return null;
        }

        // in case of space padding by a client we trim for the initial detection, on fallback use
        // the un-trimmed value.
        String originalName = name;
        name = name.trim();

        String[] destinations = name.split(",");
        if (destinations == null || destinations.length == 0) {
            destinations = new String[] { name };
        }

        StringBuilder destinationBuilder = new StringBuilder();
        for (int i = 0; i < destinations.length; ++i) {
            String destinationName = destinations[i];

            if (destinationName.startsWith("/queue/")) {
                destinationName = destinationName.substring("/queue/".length(), destinationName.length());
                destinationBuilder.append(ActiveMQDestination.QUEUE_QUALIFIED_PREFIX + destinationName);
            } else if (destinationName.startsWith("/topic/")) {
                destinationName = destinationName.substring("/topic/".length(), destinationName.length());
                destinationBuilder.append(ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + destinationName);
            } else if (destinationName.startsWith("/remote-temp-queue/")) {
                destinationName = destinationName.substring("/remote-temp-queue/".length(), destinationName.length());
                destinationBuilder.append(ActiveMQDestination.TEMP_QUEUE_QUALIFED_PREFIX + destinationName);
            } else if (destinationName.startsWith("/remote-temp-topic/")) {
                destinationName = destinationName.substring("/remote-temp-topic/".length(), destinationName.length());
                destinationBuilder.append(ActiveMQDestination.TEMP_TOPIC_QUALIFED_PREFIX + destinationName);
            } else if (destinationName.startsWith("/temp-queue/")) {
                ActiveMQDestination converted = converter.createTempDestination(destinationName, false);
                destinationBuilder.append(converted.getQualifiedName());
            } else if (destinationName.startsWith("/temp-topic/")) {
                ActiveMQDestination converted = converter.createTempDestination(destinationName, true);
                destinationBuilder.append(converted.getQualifiedName());
            } else {
                if (forceFallback) {
                    String fallbackName = destinationName;
                    if (destinationName.length() == 1) {
                        // Use the original non-trimmed name instead
                        fallbackName = originalName;
                    }

                    try {
                        ActiveMQDestination fallback = ActiveMQDestination.getUnresolvableDestinationTransformer().transform(fallbackName);
                        if (fallback != null) {
                            destinationBuilder.append(fallback.getQualifiedName());
                        }
                    } catch (JMSException e) {
                        throw new ProtocolException("Illegal destination name: [" + fallbackName + "] -- ActiveMQ STOMP destinations "
                                + "must begin with one of: /queue/ /topic/ /temp-queue/ /temp-topic/", false, e);
                    }
                } else {
                    throw new ProtocolException("Illegal destination name: [" + originalName + "] -- ActiveMQ STOMP destinations "
                                                + "must begin with one of: /queue/ /topic/ /temp-queue/ /temp-topic/");
                }
            }

            if (i < destinations.length - 1) {
                destinationBuilder.append(",");
            }
        }

        LOG.trace("New Composite Destination name: {}", destinationBuilder);

        return ActiveMQDestination.createDestination(destinationBuilder.toString(), ActiveMQDestination.QUEUE_TYPE);
    }
}
