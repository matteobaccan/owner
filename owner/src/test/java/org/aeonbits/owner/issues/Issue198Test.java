/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.handlers.ValueHandler;
import org.aeonbits.owner.loaders.Loader;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * See: https://github.com/matteobaccan/owner/issues/198
 * <p>
 * StFS wanted a configuration backed by Redis or etcd: a <code>Properties</code> subclass that answers
 * <code>getProperty</code> by asking the store, one key at a time, so that a store holding a hundred
 * thousand keys is never asked for all of them. He found that this library iterates the properties it is
 * given, and asked whether that could be avoided.
 * </p>
 * <p>
 * <b>The iteration happens once, when the configuration is created, and never again</b> — what the
 * configuration answers with afterwards is a map of its own, read by key. Two consequences are measured
 * here, and the second is the one that made his plan impossible rather than merely wasteful: <b>a
 * {@link Loader} never gets to supply its own <code>Properties</code></b>, it is handed an ordinary one to
 * fill, and a lazy subclass passed in as an import has its entries copied — so an override of
 * <code>getProperty</code> is never consulted.
 * </p>
 * <p>
 * The shape that does what he asked for arrived with 2.0.0 and is the third test here: a
 * {@link ValueHandler} resolves a value <b>when it is read</b>, so the store is asked for the keys that
 * are read and for no others.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue198Test {

    // ----------------------------------------------------------------------------------------------
    // What a loader can and cannot do
    // ----------------------------------------------------------------------------------------------

    /** A store behind a loader, counting how often it is asked for everything it has. */
    public static class CountingLoader implements Loader {

        private static final long serialVersionUID = 1L;

        final AtomicInteger loads = new AtomicInteger();

        @Override
        public boolean accept(URI uri) {
            return "store".equals(uri.getScheme());
        }

        @Override
        public void load(Properties result, URI uri) {
            loads.incrementAndGet();
            result.setProperty("foo.bar", "from the store");
        }
    }

    @Sources("store:everything")
    public interface FromAStore extends Config {
        @Key("foo.bar")
        String fooBar();
    }

    /**
     * The store is asked once, when the configuration is created — not per property and not per read. For
     * a remote store that is the good half of the answer: a hundred reads are one round trip, and
     * <code>reload()</code> is how you ask for a fresh one.
     */
    @Test
    public void aLoaderIsAskedOnceWhenTheConfigurationIsCreated() {
        CountingLoader loader = new CountingLoader();
        Factory factory = ConfigFactory.newInstance();
        factory.registerLoader(loader);

        FromAStore config = factory.create(FromAStore.class);
        assertEquals("asked while the configuration was being built", 1, loader.loads.get());

        for (int i = 0; i < 5; i++)
            assertEquals("from the store", config.fooBar());

        assertEquals("and not again, whatever is read", 1, loader.loads.get());
    }

    // ----------------------------------------------------------------------------------------------
    // Why the Properties subclass in the issue could not work
    // ----------------------------------------------------------------------------------------------

    /** What StFS wanted to write: a Properties that answers by asking a store, one key at a time. */
    public static class LazyProperties extends Properties {

        private static final long serialVersionUID = 1L;

        @Override
        public String getProperty(String key) {
            return "asked the store for " + key;
        }
    }

    public interface Plain extends Config {
        @DefaultValue("nothing was imported")
        String foo();
    }

    /**
     * <b>The override is never consulted.</b> An import is merged into the properties of the configuration
     * — entry by entry, which is what <code>Hashtable.putAll</code> does — so what arrives is what the
     * object <i>contains</i> and never what it would have <i>computed</i>. A lazy subclass contains
     * nothing, so nothing is imported, and it is silent about it.
     */
    @Test
    public void aLazyPropertiesSubclassImportsNothingAtAll() {
        Plain config = ConfigFactory.create(Plain.class, new LazyProperties());

        assertEquals("nothing was imported", config.foo());
        assertNotEquals("asked the store for foo", config.foo());
    }

    // ----------------------------------------------------------------------------------------------
    // What answers the question he actually asked
    // ----------------------------------------------------------------------------------------------

    /** The same store, reached one key at a time, when the key is read. */
    public static class StoreHandler implements ValueHandler {

        private static final long serialVersionUID = 1L;

        final AtomicInteger asked = new AtomicInteger();
        final List<String> keys = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public String name() {
            return "store198";
        }

        @Override
        public String resolve(String payload) {
            asked.incrementAndGet();
            keys.add(payload);
            return "value of " + payload;
        }
    }

    public interface Lazily extends Config {
        @DefaultValue("${$store198::foo.bar}")
        String fooBar();

        @DefaultValue("${$store198::never.read}")
        String neverRead();
    }

    /**
     * <b>Read a key, ask the store; leave a key alone, and the store never hears of it.</b> This is what
     * #198 asked for — "requesting the key values as needed" — and it arrived with 2.0.0, as the mechanism
     * that reads an encrypted value out of a marker. What it costs is a line per key in a local file,
     * which is also a readable list of what the application reads.
     */
    @Test
    public void aValueHandlerIsAskedWhenTheValueIsRead() {
        StoreHandler handler = new StoreHandler();
        Factory factory = ConfigFactory.newInstance();
        factory.registerValueHandler(handler);

        Lazily config = factory.create(Lazily.class);
        assertEquals("nothing was read yet, so the store was not asked", 0, handler.asked.get());

        assertEquals("value of foo.bar", config.fooBar());
        assertEquals("one read, one question", 1, handler.asked.get());

        config.fooBar();
        assertEquals("and the value is resolved again on the next read, so caching is the handler's own "
                + "business - which is what the shipped ciphers do", 2, handler.asked.get());

        assertTrue(handler.keys.toString(), handler.keys.contains("foo.bar"));
        assertFalse("the key nobody read was never asked for, which is the whole of #198",
                handler.keys.contains("never.read"));
    }
}
