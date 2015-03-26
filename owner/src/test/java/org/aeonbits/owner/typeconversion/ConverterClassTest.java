/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.typeconversion;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Luigi R. Viggiano
 */
public class ConverterClassTest {
    private MyConfig cfg;
    static class Server {
        private final String name;
        private final Integer port;

        public Server(String name, Integer port) {
            this.name = name;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Server server = (Server) o;

            if (!name.equals(server.name)) return false;
            if (!port.equals(server.port)) return false;

            return true;
        }

        @Override
        public String toString() {
            return name + ":" + port;
        }
    }

    public static class ServerConverter implements Converter<Server> {
        public Server convert(Method targetMethod, String text) {
            String[] split = text.split(":", -1);
            String name = split[0];
            Integer port = 80;
            if (split.length >= 2)
                port = Integer.valueOf(split[1]);
            return new Server(name, port);
        }
    }

    public static class ReturningNullConverter implements Converter<Server> {
        public Server convert(Method method, String input) {
            return null;
        }
    }

    public static class ReturningUnsupportedOperationException implements Converter<Server> {
        public Server convert(Method method, String input) {
            throw new UnsupportedOperationException(String.format("Cannot convert %s to %s", input, Server.class));
        }
    }

    public static class ReturningNullPointerException implements Converter<Server> {
        public Server convert(Method method, String input) {
            throw new NullPointerException();
        }
    }

    public abstract static class CantBeInstantiated implements Converter<Server> { // abstract
        public Server convert(Method method, String input) {
            return null;
        }
    }

    private static class CantBeAccessed implements Converter<Server> { // private
        public Server convert(Method method, String input) {
            return null;
        }
    }

    interface MyConfig extends Config {
        @DefaultValue("foobar.com:8080")
        @ConverterClass(ServerConverter.class)
        Server server();

        @DefaultValue("google.com, yahoo.com:8080, owner.aeonbits.org:4000")
        @ConverterClass(ServerConverter.class)
        Server[] servers();

        @DefaultValue("foobar:80")
        @ConverterClass(ReturningNullConverter.class)
        Server returningNull();

        @DefaultValue("foobar:80")
        @ConverterClass(ReturningUnsupportedOperationException.class)
        Server returningUnsupportedOperationException();

        @DefaultValue("foobar:80")
        @ConverterClass(ReturningNullPointerException.class)
        Server returningNullPointerException();

        @DefaultValue("foobar:80")
        @ConverterClass(CantBeInstantiated.class)
        Server converterClassCantBeInstantiated();

        @DefaultValue("foobar:80")
        @ConverterClass(CantBeAccessed.class)
        Server converterClassCantBeAccessed();

        @DefaultValue("10")
        @ConverterClass(OverridesIntegerConversion.class)
        int overridden();
    }

    @Before
    public void before() {
        this.cfg = ConfigFactory.create(MyConfig.class);
    }

    @Test
    public void testOverriddenConversion() {
        assertEquals(42, cfg.overridden());
    }


    @Test
    public void testSingleObject() {
        assertEquals(new Server("foobar.com", 8080), cfg.server());
    }

    @Test
    public void testArrayObject() {
        Server[] expected = new Server[] {
                new Server("google.com", 80),
                new Server("yahoo.com", 8080),
                new Server("owner.aeonbits.org", 4000)
        };
        assertArrayEquals(expected, cfg.servers());
    }

    @Test
    public void testReturningNull() {
        assertNull(cfg.returningNull());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReturningUnsupportedOperationException() {
        cfg.returningUnsupportedOperationException();
    }

    @Test(expected = NullPointerException.class)
    public void testReturningNullPointerException() {
        cfg.returningNullPointerException();
    }

    @Test
    public void testConverterCantBeInstantiated() {
        try {
            cfg.converterClassCantBeInstantiated();
            fail("exception expected");
        } catch (UnsupportedOperationException ex) {
            assertEquals(InstantiationException.class, ex.getCause().getClass());
        }
    }

    @Test
    public void testConverterCantBeAccessed() {
        try {
            cfg.converterClassCantBeAccessed();
            fail("exception expected");
        } catch (UnsupportedOperationException ex) {
            assertEquals(IllegalAccessException.class, ex.getCause().getClass());
        }
    }

    public static class OverridesIntegerConversion implements Converter {
        public Object convert(Method method, String input) {
            return 42;
        }
    }
}
