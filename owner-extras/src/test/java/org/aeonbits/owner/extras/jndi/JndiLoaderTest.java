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
import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The JNDI loader: which names it accepts, which it refuses, and what it makes of what is bound.
 *
 * @author Matteo Baccan
 */
public class JndiLoaderTest {

    private final JndiLoader loader = new JndiLoader();

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

    private Properties load(String spec) throws IOException {
        Properties loaded = new Properties();
        loader.load(loaded, URI.create(spec));
        return loaded;
    }

    // --- which sources it answers for -----------------------------------------------------------

    @Test
    public void itAnswersForJndiAndForJava() {
        assertTrue(loader.accept(URI.create("jndi:comp/env/myconfig")));
        assertTrue(loader.accept(URI.create("java:comp/env/myconfig")));

        assertFalse(loader.accept(URI.create("file:app.properties")));
        assertFalse(loader.accept(URI.create("classpath:app.properties")));
        assertFalse(loader.accept(URI.create("zookeeper://host:2181/app")));
        assertFalse("a source need not have a scheme at all", loader.accept(URI.create("app.properties")));
    }

    // --- the names ------------------------------------------------------------------------------

    /** A relative name and the container-qualified one are the same name written two ways. */
    @Test
    public void aRelativeNameIsResolvedAgainstTheContainerContext() throws IOException {
        InMemoryContextFactory.bind("java:comp/env/myconfig", context("port", "8080"));

        assertEquals("8080", load("jndi:myconfig").getProperty("port"));
        assertEquals("8080", load("jndi:comp/env/myconfig").getProperty("port"));
        assertEquals("8080", load("java:comp/env/myconfig").getProperty("port"));
    }

    /**
     * The commonest source of all, and the one the first rule got wrong: <code>&lt;env-entry&gt;</code>
     * elements are bound <b>directly under</b> <code>java:comp/env</code>, so reading the whole of that
     * context is what most deployments will write. Resolved as a relative name it became
     * <code>java:comp/env/comp/env</code> and found nothing.
     */
    @Test
    public void theWholeComponentContextIsReadable() throws IOException {
        Map<String, Object> entries = context("host", "localhost");
        entries.put("port", 8080);
        InMemoryContextFactory.bind("java:comp/env", entries);

        assertEquals("localhost", load("jndi:comp/env").getProperty("host"));
        assertEquals("8080", load("java:comp/env").getProperty("port"));
    }

    /** A name under the component namespace but outside <code>env</code> is not re-qualified either. */
    @Test
    public void aComponentNameOutsideEnvIsNotResolvedAgainstEnv() throws IOException {
        InMemoryContextFactory.bind("java:comp/other", context("k", "v"));
        assertEquals("v", load("jndi:comp/other").getProperty("k"));
    }

    @Test
    public void aJavaNameThatIsNotUnderCompEnvIsUsedAsWritten() throws IOException {
        InMemoryContextFactory.bind("java:global/myconfig", context("port", "9090"));
        assertEquals("9090", load("java:global/myconfig").getProperty("port"));
    }

    /**
     * The refusal this class exists for. A JNDI name can carry its own scheme and
     * <code>InitialContext</code> follows it over the network — which is the shape of Log4Shell, reachable
     * from a configuration file because a source spec is expanded before it is read.
     */
    @Test
    public void aNameThatWouldLeaveThisMachineIsRefused() {
        for (String remote : new String[]{
                "jndi:ldap://evil.example/dc=x",
                "jndi:ldaps://evil.example/dc=x",
                "jndi:rmi://evil.example:1099/payload",
                "jndi:iiop://evil.example/x",
                "jndi:dns://evil.example/x"}) {
            try {
                load(remote);
                fail(remote + " should not be resolved");
            } catch (UnsupportedOperationException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("local names only"));
            } catch (IOException e) {
                fail(remote + " reached a lookup: " + e.getMessage());
            }
        }
    }

    /** A colon is legal inside a JNDI name; only a leading one is a scheme. */
    @Test
    public void aColonInsideTheNameIsNotAScheme() throws IOException {
        InMemoryContextFactory.bind("java:comp/env/some:name", context("k", "v"));
        assertEquals("v", load("jndi:comp/env/some:name").getProperty("k"));
    }

    /**
     * <code>jndi:</code> on its own never reaches the loader — <code>URI</code> refuses it as a syntax
     * error before anything of ours is asked — so the guard is exercised where it lives.
     */
    @Test
    public void aSourceNamingNothingIsRefused() {
        try {
            URI.create("jndi:");
            fail("URI used to refuse this; if it no longer does, the loader has a new case to cover");
        } catch (IllegalArgumentException expectedFromUri) {
            assertTrue(expectedFromUri.getMessage(), expectedFromUri.getMessage().contains("scheme-specific"));
        }

        try {
            JndiNames.resolve("   ", "jndi:");
            fail("there is nothing to look up");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("names nothing"));
        }
    }

    // --- what it reads --------------------------------------------------------------------------

    @Test
    public void everyBindingUnderTheContextBecomesAProperty() throws IOException {
        Map<String, Object> bound = context("host", "localhost");
        bound.put("port", 8080);
        bound.put("enabled", Boolean.TRUE);
        bound.put("separator", ';');
        bound.put("timeout", 30L);
        InMemoryContextFactory.bind("java:comp/env/myconfig", bound);

        Properties loaded = load("jndi:comp/env/myconfig");
        assertEquals("localhost", loaded.getProperty("host"));
        assertEquals("8080", loaded.getProperty("port"));
        assertEquals("true", loaded.getProperty("enabled"));
        assertEquals(";", loaded.getProperty("separator"));
        assertEquals("30", loaded.getProperty("timeout"));
    }

    /** Subcontexts flatten with a dot, which is the convention every other format here uses. */
    @Test
    public void aSubcontextIsReadAndItsNameJoinedWithADot() throws IOException {
        Map<String, Object> db = context("host", "db.internal");
        db.put("port", 5432);
        Map<String, Object> root = context("name", "myapp");
        root.put("db", db);
        InMemoryContextFactory.bind("java:comp/env/myconfig", root);

        Properties loaded = load("jndi:comp/env/myconfig");
        assertEquals("myapp", loaded.getProperty("name"));
        assertEquals("db.internal", loaded.getProperty("db.host"));
        assertEquals("5432", loaded.getProperty("db.port"));
    }

    /**
     * A naming tree that comes back to itself. JNDI has links and aliases, so a context can contain one of
     * its own ancestors — and reading it used to follow that until the stack ran out, which is a
     * StackOverflowError with no indication of what was being read.
     */
    @Test
    public void aContextThatContainsItselfIsRefusedRatherThanFollowed() {
        Map<String, Object> loop = context("host", "localhost");
        loop.put("itself", loop);
        InMemoryContextFactory.bind("java:comp/env/myconfig", loop);

        try {
            load("jndi:comp/env/myconfig");
            fail("that tree has no bottom");
        } catch (StackOverflowError followedIt) {
            fail("the recursion was followed instead of being bounded");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("levels deep"));
        }
    }

    /**
     * The judgement that makes this loader usable at all: a real <code>java:comp/env</code> holds a
     * DataSource beside the settings, and refusing the whole context over it would be refusing the
     * container this exists for.
     */
    @Test
    public void aBindingThatIsNotAScalarIsSkippedRatherThanRefused() throws IOException {
        Map<String, Object> bound = context("host", "localhost");
        bound.put("dataSource", new Object());
        InMemoryContextFactory.bind("java:comp/env/myconfig", bound);

        Properties loaded = load("jndi:comp/env/myconfig");
        assertEquals("the settings beside it still arrive", "localhost", loaded.getProperty("host"));
        assertNull("and the resource does not", loaded.getProperty("dataSource"));
    }

    /** A source is a set of keys. One value has no key of its own, so it is the handler's business. */
    @Test
    public void aNameBoundToOneValueIsRefusedAndPointsAtTheHandler() {
        InMemoryContextFactory.bind("java:comp/env/db/password", "s3cr3t");
        try {
            load("jndi:comp/env/db/password");
            fail("that is a value, not a source");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("${$jndi::"));
        }
    }

    @Test
    public void aNameNothingIsBoundToIsAnIOException() {
        try {
            load("jndi:comp/env/absent");
            fail("nothing is bound there");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("java:comp/env/absent"));
        }
    }

    // --- the environment ------------------------------------------------------------------------

    /** The deliberate way to reach a provider elsewhere, which is what the refusal above sends you to. */
    @Test
    public void anEnvironmentOfYourOwnReachesTheProvider() throws IOException {
        InMemoryContextFactory.bind("java:comp/env/myconfig", context("port", "8080"));

        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, InMemoryContextFactory.class.getName());
        environment.put(Context.PROVIDER_URL, "an-address-of-your-own");

        Properties loaded = new Properties();
        new JndiLoader(environment).load(loaded, URI.create("jndi:myconfig"));

        assertEquals("8080", loaded.getProperty("port"));
        assertEquals("an-address-of-your-own",
                InMemoryContextFactory.lastEnvironment.get(Context.PROVIDER_URL));
    }

    @Test
    public void theEnvironmentIsCopiedSoTheCallerMayKeepUsingItsOwn() throws IOException {
        InMemoryContextFactory.bind("java:comp/env/myconfig", context("port", "8080"));

        Hashtable<String, String> mine = new Hashtable<>();
        mine.put(Context.INITIAL_CONTEXT_FACTORY, InMemoryContextFactory.class.getName());
        JndiLoader configured = new JndiLoader(mine);
        mine.clear();

        Properties loaded = new Properties();
        configured.load(loaded, URI.create("jndi:myconfig"));
        assertEquals("8080", loaded.getProperty("port"));
    }

    // --- end to end -----------------------------------------------------------------------------

    /** The scenario of the report: JNDI overriding what ships with the application. */
    @Config.LoadPolicy(Config.LoadType.MERGE)
    @Config.Sources({"jndi:comp/env/myconfig", "classpath:org/aeonbits/owner/extras/jndi/fallback.properties"})
    public interface MergedConfig extends Config {
        String host();

        int port();

        String fromTheFileOnly();
    }

    @Test
    public void jndiOverridesTheFileAndTheFileFillsWhatJndiDoesNotSay() {
        Map<String, Object> bound = context("host", "from-jndi");
        bound.put("port", 9999);
        InMemoryContextFactory.bind("java:comp/env/myconfig", bound);

        MergedConfig config = ConfigFactory.create(MergedConfig.class);
        assertEquals("from-jndi", config.host());
        assertEquals(9999, config.port());
        assertEquals("only in the file", config.fromTheFileOnly());
    }

    /**
     * A <code>java:</code> spec has to survive the URI factory as well as the loader — that factory has
     * special handling for <code>classpath:</code> and <code>file:</code>, and everything else falls
     * through to a plain URI, which is what this pins down.
     */
    @Config.Sources("java:comp/env/myconfig")
    public interface JavaSchemeConfig extends Config {
        String host();
    }

    @Test
    public void aJavaSchemeSourceWorksEndToEndAndNotOnlyInTheLoader() {
        InMemoryContextFactory.bind("java:comp/env/myconfig", context("host", "from-java-scheme"));
        assertEquals("from-java-scheme", ConfigFactory.create(JavaSchemeConfig.class).host());
    }

    private static Map<String, Object> context(String key, Object value) {
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put(key, value);
        return entries;
    }
}
