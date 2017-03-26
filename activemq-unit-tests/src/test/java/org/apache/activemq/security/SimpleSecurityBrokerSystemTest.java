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
package org.apache.activemq.security;

import junit.framework.Test;
import org.apache.activemq.CombinationTestSupport;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.filter.DestinationMap;
import org.apache.activemq.jaas.GroupPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.jms.Queue;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.Principal;
import java.util.*;

/**
 * Tests that the broker allows/fails access to destinations based on the
 * security policy installed on the broker.
 *
 *
 */
public class SimpleSecurityBrokerSystemTest extends SecurityTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleSecurityBrokerSystemTest.class);

    public static final GroupPrincipal GUESTS = new GroupPrincipal("guests");
    public static final GroupPrincipal USERS = new GroupPrincipal("users");
    public static final GroupPrincipal ADMINS = new GroupPrincipal("admins");
    public static Principal WILDCARD;
    static {
        try {
         WILDCARD = (Principal) DefaultAuthorizationMap.createGroupPrincipal("*", GroupPrincipal.class.getName());
        } catch (Exception e) {
            LOG.error("Failed to make wildcard principal", e);
        }
    }

    public BrokerPlugin authorizationPlugin;
    public BrokerPlugin authenticationPlugin;

    static {
        String path = System.getProperty("java.security.auth.login.config");
        if (path == null) {
            URL resource = SimpleSecurityBrokerSystemTest.class.getClassLoader().getResource("login.config");
            if (resource != null) {
                path = resource.getFile();
                System.setProperty("java.security.auth.login.config", path);
            }
        }
        LOG.info("Path to login config: " + path);
    }

    public static Test suite() {
        return suite(SimpleSecurityBrokerSystemTest.class);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    /**
     * @throws javax.jms.JMSException
     */
    public void testPopulateJMSXUserID() throws Exception {
        Session session = setupQueueWithOneMessage("TEST");

        // And also via JMS.
        MessageConsumer consumer = session.createConsumer(destination);
        Message m = consumer.receive(1000);
        assertTrue(m.propertyExists("JMSXUserID"));
        assertEquals("system",  m.getStringProperty("JMSXUserID"));
    }

    public void testAuthorizedBrowsingForAdmin() throws Exception {
        Session session = setupQueueWithOneMessage("TESTBROWSER");

        // try to browse as system
        QueueBrowser browser = session.createBrowser((Queue)destination);
        List<Message> messages = Collections.list(browser.getEnumeration());
        assertTrue(messages.size() == 1);
        Message m = messages.get(0);
        assertTrue(m.propertyExists("JMSXUserID"));
        assertEquals("system",  m.getStringProperty("JMSXUserID"));
    }

    public void testUnauthorizedBrowsingForGuest() throws Exception {
        //create a queue only for admin
        setupQueueWithOneMessage("USERS.TESTFAILBROWSER");
        //try to browse as guest
        Session session = connectAndCreateSession("guest", "password");
        QueueBrowser browser = session.createBrowser((Queue) destination);

        try {
            // Bad guest account
            Collections.list(browser.getEnumeration());
            fail("Expected JMSSecurityException.");
        } catch (JMSSecurityException e) {
            // NOOP
        }
    }

    /**
     * send dummy message to a given queue
     */
    private Session setupQueueWithOneMessage(String queueName) throws Exception {

        destination = new ActiveMQQueue(queueName);
        Session session = connectAndCreateSession("system", "manager");
        sendMessages(session, destination, 1);
        assertExposedJMSXUserID(queueName);
        return session;
    }

    private Session connectAndCreateSession(String userName, String password) throws JMSException {
        Connection connection = factory.createConnection(userName, password);
        connections.add(connection);
        connection.start();

        //send one message
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    private static void assertExposedJMSXUserID(String destinationName) throws Exception {
        // make sure that the JMSXUserID is exposed over JMX
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        CompositeData[] browse = (CompositeData[]) mbs.invoke(new ObjectName("org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Queue,destinationName="+destinationName), "browse", null, null);
        assertEquals("system", browse[0].get("JMSXUserID"));
    }

    public static AuthorizationMap createAuthorizationMap() {
        DestinationMap readAccess = new DefaultAuthorizationMap();
        readAccess.put(new ActiveMQQueue(">"), ADMINS);
        readAccess.put(new ActiveMQQueue("USERS.>"), USERS);
        readAccess.put(new ActiveMQQueue("GUEST.>"), GUESTS);
        readAccess.put(new ActiveMQTopic(">"), ADMINS);
        readAccess.put(new ActiveMQTopic("USERS.>"), USERS);
        readAccess.put(new ActiveMQTopic("GUEST.>"), GUESTS);

        DestinationMap browseAccess = new DefaultAuthorizationMap();
        browseAccess.put(new ActiveMQQueue(">"), ADMINS);
        browseAccess.put(new ActiveMQQueue("USERS.>"), USERS);
        browseAccess.put(new ActiveMQQueue("GUEST.>"), GUESTS);
        browseAccess.put(new ActiveMQTopic(">"), ADMINS);
        browseAccess.put(new ActiveMQTopic("USERS.>"), USERS);
        browseAccess.put(new ActiveMQTopic("GUEST.>"), GUESTS);

        DestinationMap writeAccess = new DefaultAuthorizationMap();
        writeAccess.put(new ActiveMQQueue(">"), ADMINS);
        writeAccess.put(new ActiveMQQueue("USERS.>"), USERS);
        writeAccess.put(new ActiveMQQueue("GUEST.>"), USERS);
        writeAccess.put(new ActiveMQQueue("GUEST.>"), GUESTS);
        writeAccess.put(new ActiveMQTopic(">"), ADMINS);
        writeAccess.put(new ActiveMQTopic("USERS.>"), USERS);
        writeAccess.put(new ActiveMQTopic("GUEST.>"), USERS);
        writeAccess.put(new ActiveMQTopic("GUEST.>"), GUESTS);

        readAccess.put(new ActiveMQTopic("ActiveMQ.Advisory.>"), WILDCARD);
        writeAccess.put(new ActiveMQTopic("ActiveMQ.Advisory.>"), WILDCARD);

        DestinationMap adminAccess = new DefaultAuthorizationMap();
        adminAccess.put(new ActiveMQTopic(">"), ADMINS);
        adminAccess.put(new ActiveMQTopic(">"), USERS);
        adminAccess.put(new ActiveMQTopic(">"), GUESTS);
        adminAccess.put(new ActiveMQQueue(">"), ADMINS);
        adminAccess.put(new ActiveMQQueue(">"), USERS);
        adminAccess.put(new ActiveMQQueue(">"), GUESTS);

        return new SimpleAuthorizationMap(writeAccess, readAccess, adminAccess, browseAccess);
    }

    public static class SimpleAuthenticationFactory implements BrokerPlugin {
        public Broker installPlugin(Broker broker) {

            HashMap<String, String> u = new HashMap<String, String>();
            u.put("system", "manager");
            u.put("user", "password");
            u.put("guest", "password");

            Map<String, Set<Principal>> groups = new HashMap<String, Set<Principal>>();
            groups.put("system", new HashSet<Principal>(Arrays.asList(new Principal[] {ADMINS, USERS})));
            groups.put("user", new HashSet<Principal>(Arrays.asList(new Principal[] {USERS})));
            groups.put("guest", new HashSet<Principal>(Arrays.asList(new Principal[] {GUESTS})));

            return new SimpleAuthenticationBroker(broker, u, groups);
        }

        public String toString() {
            return "SimpleAuthenticationBroker";
        }
    }

    /**
     * @see {@link CombinationTestSupport}
     */
    public void initCombos() {
        addCombinationValues("authorizationPlugin",
                             new Object[] {new AuthorizationPlugin(createAuthorizationMap())});
        addCombinationValues("authenticationPlugin", new Object[] {new SimpleAuthenticationFactory()/*,
                                                                   new JaasAuthenticationPlugin()*/});
    }

    protected BrokerService createBroker() throws Exception {
        BrokerService broker = super.createBroker();
        broker.setPopulateJMSXUserID(true);
        broker.setUseAuthenticatedPrincipalForJMSXUserID(true);
        broker.setPlugins(new BrokerPlugin[] {authorizationPlugin, authenticationPlugin});
        broker.setPersistent(false);
        return broker;
    }

}
