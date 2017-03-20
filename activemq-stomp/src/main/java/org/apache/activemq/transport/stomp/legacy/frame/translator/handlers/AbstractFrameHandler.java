package org.apache.activemq.transport.stomp.legacy.frame.translator.handlers;

import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.transport.stomp.ProtocolException;
import org.apache.activemq.transport.stomp.StompFrame;

import javax.jms.JMSException;

abstract public class AbstractFrameHandler {
    protected AbstractFrameHandler next;

    public AbstractFrameHandler setNext(AbstractFrameHandler nextHandler) {
        next = nextHandler;
        return nextHandler;
    }

    public abstract ActiveMQMessage handle(StompFrame command) throws JMSException, ProtocolException;
}
