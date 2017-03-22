/*
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
package org.apache.activemq.transport.amqp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jms.*;
import javax.jms.Queue;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.filter.DestinationMap;
import org.apache.activemq.jaas.GroupPrincipal;
import org.apache.activemq.junit.ActiveMQTestRunner;
import org.apache.activemq.junit.Repeat;
import org.apache.activemq.security.*;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for various QueueBrowser scenarios with an AMQP JMS client.
 */
@RunWith(ActiveMQTestRunner.class)
public class JMSQueueBrowserTest extends JMSClientTestSupport {

    protected static final Logger LOG = LoggerFactory.getLogger(JMSClientTest.class);
    public static final GroupPrincipal USERS = new GroupPrincipal("users");
    public static final GroupPrincipal BROWSERS = new GroupPrincipal("browsers");

    public static AuthorizationMap createAuthorizationMap() {
        DestinationMap readAccess = new DefaultAuthorizationMap();
        readAccess.put(new ActiveMQQueue(">"), USERS);
        readAccess.put(new ActiveMQQueue(">"), BROWSERS);

        DestinationMap browseAccess = new DefaultAuthorizationMap();
        browseAccess.put(new ActiveMQQueue(">"), BROWSERS);
//
        DestinationMap writeAccess = new DefaultAuthorizationMap();
        writeAccess.put(new ActiveMQQueue(">"), USERS);

        DestinationMap adminAccess = new DefaultAuthorizationMap();
        adminAccess.put(new ActiveMQQueue(">"), USERS);

        return new SimpleAuthorizationMap(writeAccess, readAccess, adminAccess, browseAccess);
    }

    @Override
    protected void addAdditionalPlugins(List<BrokerPlugin> plugins) throws Exception {
        plugins.add(broker -> {
            HashMap<String, String> u = new HashMap<>();
            u.put("user", "password");
            u.put("browser", "password");

            Map<String, Set<Principal>> groups = new HashMap<>();
            groups.put("user", Stream.of(USERS).collect(Collectors.toSet()));
            groups.put("browser", Stream.of(BROWSERS).collect(Collectors.toSet()));

            return new SimpleAuthenticationBroker(broker, u, groups);
        });
        plugins.add(new AuthorizationPlugin(createAuthorizationMap()));
    }

    @Test(timeout = 60000)
    @Repeat(repetitions = 5)
    public void testBrowseAllInQueueZeroPrefetch() throws Exception {

        final int MSG_COUNT = 5;

        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI("jms.prefetchPolicy.all=0"));
        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), MSG_COUNT, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(MSG_COUNT, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (count < MSG_COUNT && enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
        }

        LOG.debug("Received all expected message, checking that hasMoreElements returns false");
        assertFalse(enumeration.hasMoreElements());
        assertEquals(5, count);
    }

    @Test(timeout = 40000)
    public void testCreateQueueBrowser() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection("user", "password");
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        session.createConsumer(queue).close();

        Connection browserConnection = cf.createConnection("browser", "password");
        browserConnection.start();
        Session browserSession = browserConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(browserSession);
        Queue browserQueue = browserSession.createQueue(getDestinationName());
        QueueBrowser browser = browserSession.createBrowser(browserQueue);
        assertNotNull(browser);
        assertEquals(0, Collections.list(browser.getEnumeration()).stream().count());

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(0, proxy.getQueueSize());
    }

    @Test(timeout = 40000)
    public void testNoMessagesBrowserHasNoElements() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        session.createConsumer(queue).close();

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(0, proxy.getQueueSize());

        Enumeration<?> enumeration = browser.getEnumeration();
        assertFalse(enumeration.hasMoreElements());
    }

    @Test(timeout=30000)
    public void testBroseOneInQueue() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(getDestinationName());
        MessageProducer producer = session.createProducer(queue);
        producer.send(session.createTextMessage("hello"));
        producer.close();

        QueueBrowser browser = session.createBrowser(queue);
        Enumeration<?> enumeration = browser.getEnumeration();
        while (enumeration.hasMoreElements()) {
            Message m = (Message) enumeration.nextElement();
            assertTrue(m instanceof TextMessage);
            LOG.debug("Browsed message {} from Queue {}", m, queue);
        }

        browser.close();

        MessageConsumer consumer = session.createConsumer(queue);
        Message msg = consumer.receive(5000);
        assertNotNull(msg);
        assertTrue(msg instanceof TextMessage);
    }

    @Test(timeout = 60000)
    @Repeat(repetitions = 5)
    public void testBrowseAllInQueue() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), 5, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(5, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertFalse(enumeration.hasMoreElements());
        assertEquals(5, count);
    }

    @Test(timeout = 60000)
    @Repeat(repetitions = 5)
    public void testBrowseAllInQueuePrefetchOne() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI("jms.prefetchPolicy.all=1"));

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), 5, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(5, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
        }
        assertFalse(enumeration.hasMoreElements());
        assertEquals(5, count);
    }

    @Test(timeout = 40000)
    public void testBrowseAllInQueueTxSession() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), 5, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(5, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
        }
        assertFalse(enumeration.hasMoreElements());
        assertEquals(5, count);
    }

    @Test(timeout = 40000)
    public void testQueueBrowserInTxSessionLeavesOtherWorkUnaffected() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI());

        connection = cf.createConnection();
        connection.start();

        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), 5, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(5, proxy.getQueueSize());

        // Send some TX work but don't commit.
        MessageProducer txProducer = session.createProducer(queue);
        for (int i = 0; i < 5; ++i) {
            txProducer.send(session.createMessage());
        }

        assertEquals(5, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
        }

        assertFalse(enumeration.hasMoreElements());
        assertEquals(5, count);

        browser.close();

        // Now check that all browser work did not affect the session transaction.
        assertEquals(5, proxy.getQueueSize());
        session.commit();
        assertEquals(10, proxy.getQueueSize());
    }

    @Test(timeout = 60000)
    public void testBrowseAllInQueueSmallPrefetch() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory(getAmqpURI("jms.prefetchPolicy.all=5"));

        connection = cf.createConnection();
        connection.start();

        final int MSG_COUNT = 30;

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        assertNotNull(session);
        Queue queue = session.createQueue(getDestinationName());
        sendMessages(name.getMethodName(), MSG_COUNT, false);

        QueueViewMBean proxy = getProxyToQueue(getDestinationName());
        assertEquals(MSG_COUNT, proxy.getQueueSize());

        QueueBrowser browser = session.createBrowser(queue);
        assertNotNull(browser);
        Enumeration<?> enumeration = browser.getEnumeration();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            Message msg = (Message) enumeration.nextElement();
            assertNotNull(msg);
            LOG.debug("Recv: {}", msg);
            count++;
        }
        assertFalse(enumeration.hasMoreElements());
        assertEquals(MSG_COUNT, count);
    }

    @Override
    protected boolean isUseOpenWireConnector() {
        return true;
    }
}
