/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.DiscoverableLoader;
import org.aeonbits.owner.loaders.Loader;
import org.aeonbits.owner.loaders.PropertiesLoader;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A discovered loader is ahead of the built-in ones when a source is being matched, and behind them when the
 * sources to look for are being guessed. The two orderings are the whole point of this class.
 *
 * @author Matteo Baccan
 */
public class LoadersManagerOrderingTest {

    private static final String PREFIX = "classpath:org/aeonbits/owner/MyConfig";

    /**
     * PropertiesLoader accepts every URL it can resolve, so a discovered loader that came after it would
     * never see one of its own files.
     */
    @Test
    public void aDiscoveredLoaderAnswersBeforeTheBuiltInOnes() throws URISyntaxException {
        Loader discovered = new DiscoverableLoader();
        LoadersManager loaders = new LoadersManager(Collections.singletonList(discovered));

        assertSame(discovered, loaders.findLoader(new URI("file:/app/config.discoverable")));
    }

    @Test
    public void withoutTheDiscoveredLoaderThatFileIsReadAsProperties() throws URISyntaxException {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>emptyList());

        assertTrue("this silence is what the CONFIG line about discovery exists to explain",
                loaders.findLoader(new URI("file:/app/config.discoverable")) instanceof PropertiesLoader);
    }

    /**
     * The other ordering. Under LoadType.FIRST the first spec that resolves is the one that answers, so a
     * discovered loader placed in front would let a jar on the classpath displace the file an application
     * already loads.
     */
    @Test
    public void aDiscoveredLoaderContributesItsDefaultSpecLast() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new DiscoverableLoader()));

        List<String> specs = Arrays.asList(loaders.defaultSpecs(PREFIX));
        assertEquals(PREFIX + ".discoverable", specs.get(specs.size() - 1));
        assertTrue(specs.toString(), specs.indexOf(PREFIX + ".properties") < specs.indexOf(PREFIX + ".discoverable"));
        assertTrue(specs.toString(), specs.indexOf(PREFIX + ".xml") < specs.indexOf(PREFIX + ".discoverable"));
    }

    /**
     * Registering is an explicit act of the application, unlike a jar turning up on the class path, so it
     * keeps the front in both orderings.
     */
    @Test
    public void aRegisteredLoaderComesFirstInBothOrderings() throws URISyntaxException {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new DiscoverableLoader()));
        Loader registered = new DiscoverableLoader() {
            @Override
            public String defaultSpecFor(String uriPrefix) {
                return uriPrefix + ".registered";
            }
        };
        loaders.registerLoader(registered);

        assertSame(registered, loaders.findLoader(new URI("file:/app/config.discoverable")));
        assertEquals(PREFIX + ".registered", loaders.defaultSpecs(PREFIX)[0]);
    }

    /** The same loader registered by hand and also found on the classpath is one name, not two. */
    @Test
    public void aRepeatedDefaultSpecIsContributedOnce() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new DiscoverableLoader()));
        loaders.registerLoader(new DiscoverableLoader());

        List<String> specs = Arrays.asList(loaders.defaultSpecs(PREFIX));
        assertEquals(specs.toString(), 1, Collections.frequency(specs, PREFIX + ".discoverable"));
    }

    @Test
    public void clearingTakesTheDiscoveredLoadersWithIt() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new DiscoverableLoader()));
        loaders.clear();

        assertEquals(0, loaders.defaultSpecs(PREFIX).length);
    }

    /**
     * A loader is free to define equality - two instances of one class are still two loaders, and only the
     * one that was discovered belongs at the back.
     */
    @Test
    public void beingDiscoveredIsAboutTheInstanceAndNotTheClass() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new EqualToAnyOfItsKind()));
        loaders.registerLoader(new EqualToAnyOfItsKind());

        List<String> specs = Arrays.asList(loaders.defaultSpecs(PREFIX));
        assertEquals(specs.toString(), PREFIX + ".kind", specs.get(0));
        assertNotEquals(specs.toString(), PREFIX + ".kind", specs.get(specs.size() - 1));
    }

    static class EqualToAnyOfItsKind implements Loader {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean accept(URI uri) {
            return false;
        }

        @Override
        public void load(java.util.Properties result, URI uri) {
            // nothing to read: this loader exists for its equality and its default spec
        }

        @Override
        public String defaultSpecFor(String uriPrefix) {
            return uriPrefix + ".kind";
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualToAnyOfItsKind;
        }

        @Override
        public int hashCode() {
            return EqualToAnyOfItsKind.class.hashCode();
        }
    }
}
