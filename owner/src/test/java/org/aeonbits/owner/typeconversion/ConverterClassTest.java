/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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
import java.util.Objects;

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
        public int hashCode() {
            return Objects.hash(name, port);
        }

        @Override
        public String toString() {
            return name + ":" + port;
        }
    }

    public static class ServerConverter implements Converter<Server> {
        @Override
        public Server convert(Method targetMethod, String text) {
            String[] split = text.split(":", -1);
            String name = split[0];
            Integer port = 80;
            if (split.length >= 2)
                try {
                    port = Integer.valueOf(split[1]);
                } catch (NumberFormatException e) {
                    throw new UnsupportedOperationException(
                            String.format("Cannot convert %s to %s", text, Server.class), e);
                }
            return new Server(name, port);
        }
    }

    public static class ReturningNullConverter implements Converter<Server> {
        @Override
        public Server convert(Method method, String input) {
            return null;
        }
    }

    public static class ReturningUnsupportedOperationException implements Converter<Server> {
        @Override
        public Server convert(Method method, String input) {
            throw new UnsupportedOperationException(String.format("Cannot convert %s to %s", input, Server.class));
        }
    }

    public static class ReturningNullPointerException implements Converter<Server> {
        @Override
        public Server convert(Method method, String input) {
            throw new NullPointerException();
        }
    }

    public abstract static class CantBeInstantiated implements Converter<Server> { // abstract
        @Override
        public Server convert(Method method, String input) {
            return null;
        }
    }

    /**
     * Private, which used to be a reason on its own: the converter was built with
     * <code>Class.newInstance()</code>, which demands a public class and a public constructor. It is
     * built like every other class named in an annotation now, so what this one shows is that being
     * private is not one - see issue #186, and Issue186Test for the whole family.
     */
    private static class OnlyVisibleHere implements Converter<Server> {
        @Override
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
        @ConverterClass(OnlyVisibleHere.class)
        Server converterThatIsOnlyVisibleWhereItIsUsed();

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

    /** A converter needs no visibility beyond the interface that names it: issue #186. */
    @Test
    public void testAConverterThatIsOnlyVisibleWhereItIsUsedIsStillCalled() {
        assertNull(cfg.converterThatIsOnlyVisibleWhereItIsUsed());
    }

    public static class OverridesIntegerConversion implements Converter {
        @Override
        public Object convert(Method method, String input) {
            return 42;
        }
    }
}
