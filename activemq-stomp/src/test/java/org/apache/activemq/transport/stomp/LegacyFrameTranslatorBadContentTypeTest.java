package org.apache.activemq.transport.stomp;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.util.DefaultTestAppender;
import org.apache.log4j.Appender;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;

import static org.junit.Assert.assertTrue;

public class LegacyFrameTranslatorBadContentTypeTest extends StompTestSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(LegacyFrameTranslatorBadContentTypeTest.class);
    private Connection connection;
    private Session session;
    private ActiveMQQueue queue;

    private boolean gotProtocolExceptionInLog = false;

    // custom Log4J appender so we can filter the logging output in this test.
    protected Appender appender = new DefaultTestAppender() {
        //@Override
        @Override
        public void doAppend(org.apache.log4j.spi.LoggingEvent event) {
            if (event.getMessage().toString().contains("ProtocolException") &&
                    event.getMessage().toString().contains("unsupported charset")) {
                gotProtocolExceptionInLog = true;
            }
        }
    };

    @Override
    protected boolean isUseTcpConnector() {
        return false;
    }


    @Override
    protected boolean isUseNioPlusSslConnector() {
        return true;
    }


    @Override
    protected Socket createSocket() throws IOException {
        SocketFactory factory = SSLSocketFactory.getDefault();
        return factory.createSocket("127.0.0.1", this.nioSslPort);
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        // register custom Log4J Appender
        org.apache.log4j.Logger.getRootLogger().addAppender(appender);

        stompConnect();
        connection = cf.createConnection("system", "manager");
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        queue = new ActiveMQQueue(getQueueName());
        connection.start();
    }


    @Override
    public void tearDown() throws Exception {
        // unregister Log4J appender
        org.apache.log4j.Logger.getRootLogger().removeAppender(appender);
    }

    @Test(timeout = 60000)
    public void testContentTypeUnknownCharset() throws Exception {
        MessageConsumer consumer = session.createConsumer(queue);

        String frame = "CONNECT\n" + "login:system\n" + "passcode:manager\n\n" + Stomp.NULL;
        stompConnection.sendFrame(frame);

        frame = stompConnection.receiveFrame();
        assertTrue(frame.startsWith("CONNECTED"));

        frame = "SEND\n" + "content-type:text/plain;charset=utf-20\n" + "destination:/queue/" + getQueueName() + "\n\n" + "Hello World" + Stomp.NULL;

        stompConnection.sendFrame(frame);
        consumer.receive(2500);

        assertTrue("ProtocolException expected but not received.", gotProtocolExceptionInLog);
    }
}
