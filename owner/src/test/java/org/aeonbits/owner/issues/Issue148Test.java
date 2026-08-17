/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.Mutable;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * See: https://github.com/matteobaccan/owner/issues/148
 * <p>
 * pramodbms asked in 2015 whether a configuration could be fetched out of the cache <b>by one of the
 * property names it declares</b> - <code>ConfigCache.get(SampleConfiguration.PORT)</code> - and nobody
 * answered for eleven years. The answer was already there when the question was asked:
 * {@link ConfigCache#getOrCreate(Object, Class, java.util.Map[])} takes any object as the key, and
 * {@link ConfigCache#get(Object)} fetches by it, both since 1.0.6.
 * </p>
 * <p>
 * The one step the question does not have is that the key is chosen when the instance is created. That is
 * not a limitation to be lifted either, and this class says why in its last test: a property name is not
 * an identity. Two interfaces reading one file may declare the same key - which is the ordinary way this
 * library is used, not an abuse of it - so a lookup by property name would have to answer with one of
 * them, and there is no reason for it to be yours.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue148Test {

    public interface SampleConfiguration extends Config, Mutable {

        String PORT = "abc.port";

        @Config.Key(PORT)
        @Config.DefaultValue("8080")
        int getPort();
    }

    /** Another interface reading the same key, which is why the shortcut cannot exist. See the last test. */
    public interface AnotherConfiguration extends Config {

        @Config.Key(SampleConfiguration.PORT)
        @Config.DefaultValue("9090")
        int port();
    }

    @After
    public void emptyTheCache() {
        ConfigCache.clear();
    }

    /** The question, written the way it was asked. */
    @Test
    public void theConfigurationIsFetchedByTheNameOfOneOfItsProperties() {
        ConfigCache.getOrCreate(SampleConfiguration.PORT, SampleConfiguration.class);

        SampleConfiguration config = ConfigCache.get(SampleConfiguration.PORT);

        assertNotNull(config);
        assertEquals(8080, config.getPort());
    }

    /** And it is the same object, which is the point of asking the cache rather than the factory. */
    @Test
    public void itIsTheSameInstanceEveryTime() {
        SampleConfiguration created = ConfigCache.getOrCreate(SampleConfiguration.PORT,
                SampleConfiguration.class);

        assertSame(created, ConfigCache.get(SampleConfiguration.PORT));
        assertSame(created, ConfigCache.getOrCreate(SampleConfiguration.PORT, SampleConfiguration.class));
    }

    /**
     * A key nobody registered answers with <code>null</code> rather than building something: the cache
     * hands back what was put in it, and a key that means nothing to it means nothing.
     */
    @Test
    public void aKeyNobodyRegisteredIsNotThere() {
        assertNull(ConfigCache.get(SampleConfiguration.PORT));
    }

    /**
     * <b>Why the step of choosing the key cannot be skipped.</b> Both interfaces declare
     * <code>abc.port</code>, which is how two mapping interfaces share one file - so "the configuration
     * that declares this property" is a question with two answers, and a library that picked one would be
     * guessing. Under an explicit key there is nothing to guess.
     */
    @Test
    public void twoConfigurationsMayDeclareOneProperty() {
        SampleConfiguration first = ConfigCache.getOrCreate("sample", SampleConfiguration.class);
        AnotherConfiguration second = ConfigCache.getOrCreate("another", AnotherConfiguration.class);

        assertEquals(8080, first.getPort());
        assertEquals(9090, second.port());
    }
}
