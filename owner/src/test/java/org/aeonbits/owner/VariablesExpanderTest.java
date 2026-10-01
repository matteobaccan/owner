/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.handlers.ValueHandler;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link VariablesExpander}.
 */
public class VariablesExpanderTest {

    @Test
    public void shouldExpandUserHomeTilde() {
        VariablesExpander expander = new VariablesExpander(new Properties());
        String userHome = System.getProperty("user.home");
        assertEquals(userHome + "/config.properties", expander.expand("~/config.properties"));
        assertEquals("file:" + userHome + "/app.properties", expander.expand("file:~/app.properties"));
    }

    @Test
    public void shouldExpandPropertyVariables() {
        Properties props = new Properties();
        props.setProperty("app.dir", "/var/app");
        props.setProperty("app.file", "config.properties");
        VariablesExpander expander = new VariablesExpander(props);

        assertEquals("file:/var/app/config.properties", expander.expand("file:${app.dir}/${app.file}"));
    }

    @Test
    public void shouldSupportDefaultValuesForVariables() {
        Properties props = new Properties();
        VariablesExpander expander = new VariablesExpander(props);

        assertEquals("file:/etc/app/config.properties", expander.expand("file:${app.dir:/etc/app}/config.properties"));
    }

    @Test
    public void shouldOverrideSystemPropertiesAndEnvVarsWithConstructorProperties() {
        String sysKey = "user.home";

        Properties props = new Properties();
        props.setProperty(sysKey, "/custom/home");
        VariablesExpander expander = new VariablesExpander(props);

        assertEquals("file:/custom/home/file.txt", expander.expand("file:${user.home}/file.txt"));
    }

    @Test
    public void shouldExpandSystemPropertiesWhenNotInConstructorProperties() {
        String sysKey = "user.home";
        String sysValue = System.getProperty(sysKey);

        VariablesExpander expander = new VariablesExpander(new Properties());
        assertEquals("file:" + sysValue + "/data", expander.expand("file:${user.home}/data"));
    }

    @Test
    public void shouldThrowExceptionInStrictModeWhenVariableIsUnresolved() {
        Properties props = new Properties();
        props.setProperty("owner.strict", "true");
        VariablesExpander expander = new VariablesExpander(props);

        try {
            expander.expand("file:${missing.property}/app.properties");
            fail("Expected UnsupportedOperationException when strict mode is enabled and variable is missing");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("missing.property"));
        }
    }

    @Test
    public void shouldReplaceMissingVariableWithEmptyStringWhenNotInStrictMode() {
        Properties props = new Properties();
        props.setProperty("owner.strict", "false");
        VariablesExpander expander = new VariablesExpander(props);

        assertEquals("file:/app.properties", expander.expand("file:${missing.property}/app.properties"));
    }

    @Test
    public void shouldWorkWithCustomHandlersManager() {
        HandlersManager handlersManager = new HandlersManager();
        handlersManager.registerValueHandler(new ValueHandler() {
            @Override
            public String name() {
                return "uppercase";
            }

            @Override
            public String resolve(String payload) {
                return payload.toUpperCase();
            }
        });

        Properties props = new Properties();
        VariablesExpander expander = new VariablesExpander(props, handlersManager);

        assertEquals("file:SECRET/app.properties", expander.expand("file:${$uppercase::secret}/app.properties"));
    }

    @Test
    public void shouldBeSerializableAndDeserializable() throws Exception {
        Properties props = new Properties();
        props.setProperty("app.name", "my-app");
        VariablesExpander expander = new VariablesExpander(props);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(expander);
        }

        VariablesExpander deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (VariablesExpander) ois.readObject();
        }

        assertEquals("file:my-app/config.properties", deserialized.expand("file:${app.name}/config.properties"));
    }
}
