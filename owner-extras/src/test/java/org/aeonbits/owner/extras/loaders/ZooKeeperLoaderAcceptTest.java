/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.loaders;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Factory;
import org.aeonbits.owner.loaders.Loader;
import org.junit.Test;

import java.net.URI;
import java.util.ServiceLoader;

import static org.aeonbits.owner.Config.Sources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What {@link ZooKeeperLoader#accept(URI)} answers, and above all what it does when the URI has no scheme
 * at all. Every registered loader is asked about every source, so a loader that reads one scheme still has
 * to survive the sources it is not interested in.
 *
 * @author Matteo Baccan
 */
public class ZooKeeperLoaderAcceptTest {

    private final ZooKeeperLoader loader = new ZooKeeperLoader();

    @Test
    public void acceptsItsOwnScheme() {
        assertTrue(loader.accept(URI.create("zookeeper://localhost:2181/config/app")));
    }

    @Test
    public void refusesAnotherScheme() {
        assertFalse(loader.accept(URI.create("file:/etc/app.properties")));
        assertFalse(loader.accept(URI.create("classpath:app.properties")));
    }

    @Test
    public void refusesAUriWithNoSchemeInsteadOfFailing() {
        assertFalse(loader.accept(URI.create("myconfig.properties")));
    }

    @Test
    public void refusesTheEmptyUriInsteadOfFailing() {
        // ConfigURIFactory builds exactly this for a blank 'file:', the case of an environment variable
        // that was named in the path of a source and is not set
        assertFalse(loader.accept(URI.create("")));
    }

    // The two below go through the library rather than calling accept directly: that is the path on which
    // this was found, and it is the outcome that matters. A source with no scheme is refused by every
    // loader, PropertiesLoader included, so the library complains that it cannot resolve one - and
    // registering this loader must leave that complaint exactly as it was, rather than replacing it with a
    // NullPointerException raised while the loaders were being asked.

    @Sources("file:")
    public interface BlankFileConfig extends Config {
        @DefaultValue("ok")
        String value();
    }

    @Sources("myconfig.properties")
    public interface SchemelessConfig extends Config {
        @DefaultValue("ok")
        String value();
    }

    @Test
    public void aBlankFileSourceFailsTheSameWayWithTheLoaderRegistered() {
        assertEquals(refusalFor(BlankFileConfig.class, false), refusalFor(BlankFileConfig.class, true));
    }

    @Test
    public void aSourceWithoutASchemeFailsTheSameWayWithTheLoaderRegistered() {
        assertEquals(refusalFor(SchemelessConfig.class, false), refusalFor(SchemelessConfig.class, true));
    }

    /**
     * The service file does its job: the loader is on the list {@link ServiceLoader} hands over, so the
     * library registers it without anybody calling <code>registerLoader</code>.
     *
     * <p>
     * This stops at discovery rather than reading a source. Going further would mean pointing at a
     * <code>zookeeper:</code> host that is not there, and Curator spends tens of seconds establishing that
     * - its own retries outlast <code>owner.zookeeper.connection.timeout.seconds</code> - to prove
     * something this test does not ask about. That a discovered loader is then registered and consulted is
     * the core's behaviour, exercised by every JSON and YAML test in <code>owner-formats</code>.
     * </p>
     */
    @Test
    public void theLoaderIsFoundOnTheClassPathWithoutBeingRegistered() {
        for (Loader found : ServiceLoader.load(Loader.class, getClass().getClassLoader()))
            if (found instanceof ZooKeeperLoader)
                return;
        fail("ZooKeeperLoader was not among the loaders declared for ServiceLoader; check "
                + "META-INF/services/org.aeonbits.owner.loaders.Loader in owner-extras");
    }

    /**
     * Creates the configuration and returns how it refused, as the exception's class and message, so that
     * the two cases can be compared rather than merely each being non-null.
     */
    private static String refusalFor(Class<? extends Config> type, boolean registerZooKeeper) {
        Factory factory = ConfigFactory.newInstance();
        if (registerZooKeeper)
            factory.registerLoader(new ZooKeeperLoader());
        try {
            factory.create(type);
            return "no refusal";
        } catch (RuntimeException refused) {
            return refused.getClass().getName() + ": " + refused.getMessage();
        }
    }
}
