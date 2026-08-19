/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.jndi;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.naming.Context;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The per-value half: <code>${$jndi::comp/env/db/password}</code>.
 *
 * @author Matteo Baccan
 */
public class JndiHandlerTest {

    private final JndiHandler handler = new JndiHandler();

    @Before
    public void useTheInMemoryProvider() {
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, InMemoryContextFactory.class.getName());
        InMemoryContextFactory.reset();
    }

    @After
    public void releaseTheProvider() {
        System.clearProperty(Context.INITIAL_CONTEXT_FACTORY);
        InMemoryContextFactory.reset();
    }

    @Test
    public void itIsNamedJndi() {
        assertEquals("jndi", handler.name());
    }

    @Test
    public void itReadsASingleEntry() {
        InMemoryContextFactory.bind("java:comp/env/db/password", "s3cr3t");
        assertEquals("s3cr3t", handler.resolve("comp/env/db/password"));
        assertEquals("s3cr3t", handler.resolve("db/password"));
        assertEquals("s3cr3t", handler.resolve("java:comp/env/db/password"));
    }

    @Test
    public void aScalarThatIsNotAStringIsReadAsOne() {
        InMemoryContextFactory.bind("java:comp/env/port", 8080);
        InMemoryContextFactory.bind("java:comp/env/enabled", Boolean.TRUE);
        assertEquals("8080", handler.resolve("port"));
        assertEquals("true", handler.resolve("enabled"));
    }

    /** The mirror of the loader's refusal: a context has no place in a value. */
    @Test
    public void aContextIsRefusedAndPointsAtSources() {
        Map<String, Object> bound = new LinkedHashMap<>();
        bound.put("host", "localhost");
        InMemoryContextFactory.bind("java:comp/env/myconfig", bound);
        try {
            handler.resolve("comp/env/myconfig");
            fail("that is a source, not a value");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("@Sources"));
        }
    }

    @Test
    public void aResourceThatIsNotAValueIsRefused() {
        InMemoryContextFactory.bind("java:comp/env/ds", new Object());
        try {
            handler.resolve("ds");
            fail("a DataSource is not a value a property can hold");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not a value"));
        }
    }

    /**
     * A marker sits in a value, which is even easier to write into than a source spec, so the refusal
     * matters here at least as much as it does in the loader.
     */
    @Test
    public void aNameThatWouldLeaveThisMachineIsRefused() {
        try {
            handler.resolve("ldap://evil.example/dc=x");
            fail("that name should not be resolved");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("local names only"));
        }
    }

    /** Throwing is the contract: the empty string would report a failure as a value. */
    @Test
    public void aNameNothingIsBoundToThrowsRatherThanAnsweringEmpty() {
        try {
            handler.resolve("absent");
            fail("nothing is bound there");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("java:comp/env/absent"));
        }
    }

    public interface DatabaseConfig extends Config {
        String password();

        String url();
    }

    /** End to end, including a value composed out of the one that came from JNDI. */
    @Test
    public void aConfigurationReadsItAndSoDoesAValueReferringToIt() {
        InMemoryContextFactory.bind("java:comp/env/db/password", "s3cr3t");
        ConfigFactory.registerValueHandler(new JndiHandler());

        DatabaseConfig config = ConfigFactory.create(DatabaseConfig.class,
                new HashMap<String, String>() {{
                    put("password", "${$jndi::comp/env/db/password}");
                    put("url", "jdbc:h2:mem:test?password=${password}");
                }});

        assertEquals("s3cr3t", config.password());
        assertEquals("jdbc:h2:mem:test?password=s3cr3t", config.url());
    }

    /**
     * The constructor that takes an environment and leaves the name alone, which is the shape somebody
     * uses who has one provider to reach but a context of their own to reach it with.
     */
    @Test
    public void aHandlerMayBeGivenAnEnvironmentAndKeepTheDefaultName() {
        Map<String, String> environment = new HashMap<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, InMemoryContextFactory.class.getName());
        InMemoryContextFactory.bind("java:comp/env/db/password", "s3cr3t");

        JndiHandler withEnvironment = new JndiHandler(environment);
        assertEquals("jndi", withEnvironment.name());
        assertEquals("s3cr3t", withEnvironment.resolve("comp/env/db/password"));
    }

    /**
     * The environment is <b>copied</b>, so a caller may go on using its own map — and a handler holding a
     * live reference to somebody else's mutable map is the kind of thing that works until it does not.
     */
    @Test
    public void theEnvironmentIsCopiedRatherThanKept() {
        Map<String, String> environment = new HashMap<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, InMemoryContextFactory.class.getName());
        InMemoryContextFactory.bind("java:comp/env/db/password", "s3cr3t");

        JndiHandler withEnvironment = new JndiHandler(environment);
        environment.clear();

        assertEquals("s3cr3t", withEnvironment.resolve("comp/env/db/password"));
    }

    /** What it says of itself, which is what a list of registered handlers prints. */
    @Test
    public void itSaysItsNameWhenPrinted() {
        assertEquals("JndiHandler[name=jndi]", new JndiHandler().toString());
        assertEquals("JndiHandler[name=other]", new JndiHandler("other", null).toString());
    }
}
