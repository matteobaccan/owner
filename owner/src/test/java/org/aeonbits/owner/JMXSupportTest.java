/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.ReflectionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the {@link JMXSupport} delegate methods exposed by the config proxy through the
 * {@link DynamicMBean} interface, without going through an MBeanServer.
 *
 * @author Matteo Baccan
 */
public class JMXSupportTest {

    private interface JMXDummyConfig extends DynamicMBean, Mutable, Reloadable {
        @DefaultValue("8080")
        int port();

        @DefaultValue("http://localhost")
        String hostname();
    }

    private JMXDummyConfig newConfig() {
        return ConfigFactory.create(JMXDummyConfig.class);
    }

    private ReflectionException invokeExpectingFailure(JMXDummyConfig config, String actionName,
                                                       Object[] params, String[] signature) throws Exception {
        try {
            config.invoke(actionName, params, signature);
            fail("expected ReflectionException for action " + actionName);
            throw new AssertionError("unreachable");
        } catch (ReflectionException e) {
            assertTrue(e.getTargetException() instanceof NoSuchMethodException);
            assertEquals(actionName, e.getTargetException().getMessage());
            return e;
        }
    }

    @Test
    public void setAttributesUpdatesEveryAttributeAndReturnsTheSameList() throws Exception {
        JMXDummyConfig config = newConfig();

        AttributeList attributes = new AttributeList();
        attributes.add(new Attribute("port", "7878"));
        attributes.add(new Attribute("hostname", "http://example.com"));

        AttributeList result = config.setAttributes(attributes);

        assertSame(attributes, result);
        assertEquals("7878", config.getAttribute("port"));
        assertEquals("http://example.com", config.getAttribute("hostname"));
    }

    @Test
    public void setAttributesWithEmptyListLeavesConfigUntouched() throws Exception {
        JMXDummyConfig config = newConfig();

        AttributeList result = config.setAttributes(new AttributeList());

        assertTrue(result.isEmpty());
        assertEquals("8080", config.getAttribute("port"));
        assertEquals("http://localhost", config.getAttribute("hostname"));
    }

    @Test
    public void invokeGetPropertyReturnsThePropertyValue() throws Exception {
        JMXDummyConfig config = newConfig();

        assertEquals("8080", config.invoke("getProperty", new Object[] { "port" }, null));
    }

    @Test
    public void invokeSetPropertyUpdatesThePropertyAndReturnsNull() throws Exception {
        JMXDummyConfig config = newConfig();

        assertNull(config.invoke("setProperty", new Object[] { "port", "9090" }, null));
        assertEquals("9090", config.getAttribute("port"));
    }

    @Test
    public void invokeReloadWithNullParamsRestoresDefaults() throws Exception {
        JMXDummyConfig config = newConfig();
        config.setProperty("port", "9090");

        assertNull(config.invoke("reload", null, null));
        assertEquals("8080", config.getAttribute("port"));
    }

    @Test
    public void invokeReloadWithEmptyParamsRestoresDefaults() throws Exception {
        JMXDummyConfig config = newConfig();
        config.setProperty("port", "9090");

        assertNull(config.invoke("reload", new Object[0], null));
        assertEquals("8080", config.getAttribute("port"));
    }

    @Test
    public void invokeGetPropertyWithNullParamsFails() throws Exception {
        invokeExpectingFailure(newConfig(), "getProperty", null, null);
    }

    @Test
    public void invokeGetPropertyWithWrongNumberOfParamsFails() throws Exception {
        invokeExpectingFailure(newConfig(), "getProperty", new Object[] { "port", "extra" }, null);
    }

    @Test
    public void invokeSetPropertyWithNullParamsFails() throws Exception {
        invokeExpectingFailure(newConfig(), "setProperty", null, null);
    }

    @Test
    public void invokeSetPropertyWithWrongNumberOfParamsFails() throws Exception {
        invokeExpectingFailure(newConfig(), "setProperty", new Object[] { "port" }, null);
    }

    @Test
    public void invokeReloadWithParamsFails() throws Exception {
        invokeExpectingFailure(newConfig(), "reload", new Object[] { "unexpected" }, null);
    }

    @Test
    public void invokeUnknownActionFails() throws Exception {
        invokeExpectingFailure(newConfig(), "unknownAction", null, null);
    }

}
