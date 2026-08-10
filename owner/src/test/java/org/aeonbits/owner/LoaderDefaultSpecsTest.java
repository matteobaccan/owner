/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.Loader;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A format may go by more than one name - <code>.yaml</code> and <code>.yml</code>, <code>.ini</code> and
 * <code>.cfg</code> - and until now a loader could offer only one.
 *
 * @author Matteo Baccan
 */
public class LoaderDefaultSpecsTest {

    private static final String PREFIX = "classpath:org/aeonbits/owner/MyConfig";

    @Test
    public void aLoaderCanOfferSeveralNamesAndTheyKeepTheirOrder() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>emptyList());
        loaders.registerLoader(new SeveralNames());

        List<String> specs = Arrays.asList(loaders.defaultSpecs(PREFIX));
        assertEquals(PREFIX + ".yaml", specs.get(0));
        assertEquals(PREFIX + ".yml", specs.get(1));
    }

    /**
     * The bridge: a loader written before this existed offers its one name through the new method without
     * being touched. Every loader in the core is one of these.
     */
    @Test
    public void aLoaderThatOffersOneNameNeedsNothingNew() {
        assertArrayEquals(new String[]{PREFIX + ".one"}, new OneName().defaultSpecsFor(PREFIX));
    }

    /**
     * Declining to be looked for is a legitimate answer - SystemLoader and DotEnvLoader both give it - so it
     * should not require writing a method that returns nothing.
     */
    @Test
    public void aLoaderThatOffersNoNameNeedsNothingAtAll() {
        assertEquals(0, new NoName().defaultSpecsFor(PREFIX).length);

        LoadersManager loaders = new LoadersManager(Collections.<Loader>emptyList());
        loaders.clear();
        loaders.registerLoader(new NoName());
        assertEquals(0, loaders.defaultSpecs(PREFIX).length);
    }

    /**
     * An empty array is how a loader says "none". A null among the names is not that - it is a mistake only
     * the author of the loader can put right, so the message names the class.
     */
    @Test
    public void aNullAmongTheNamesIsRefusedAndSaysWhoseFaultItIs() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>emptyList());
        loaders.registerLoader(new ANullAmongThem());

        try {
            loaders.defaultSpecs(PREFIX);
            fail("a null default specification should be refused");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(ANullAmongThem.class.getName()));
            assertTrue(e.getMessage(), e.getMessage().contains("null among its default specifications"));
        }
    }

    /** Several names from a discovered loader are still all of them at the back. */
    @Test
    public void severalNamesFromADiscoveredLoaderAreAllLast() {
        LoadersManager loaders = new LoadersManager(Collections.<Loader>singletonList(new SeveralNames()));

        List<String> specs = Arrays.asList(loaders.defaultSpecs(PREFIX));
        assertEquals(PREFIX + ".yml", specs.get(specs.size() - 1));
        assertEquals(PREFIX + ".yaml", specs.get(specs.size() - 2));
        assertTrue(specs.toString(), specs.indexOf(PREFIX + ".properties") < specs.indexOf(PREFIX + ".yaml"));
    }

    private abstract static class Quiet implements Loader {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean accept(URI uri) {
            return false;
        }

        @Override
        public void load(Properties result, URI uri) {
            // these loaders exist for what they offer to be looked for, not for what they read
        }
    }

    static class SeveralNames extends Quiet {
        private static final long serialVersionUID = 1L;

        @Override
        public String[] defaultSpecsFor(String uriPrefix) {
            return new String[]{uriPrefix + ".yaml", uriPrefix + ".yml"};
        }
    }

    static class OneName extends Quiet {
        private static final long serialVersionUID = 1L;

        @Override
        public String defaultSpecFor(String uriPrefix) {
            return uriPrefix + ".one";
        }
    }

    static class NoName extends Quiet {
        private static final long serialVersionUID = 1L;
    }

    static class ANullAmongThem extends Quiet {
        private static final long serialVersionUID = 1L;

        @Override
        public String[] defaultSpecsFor(String uriPrefix) {
            return new String[]{uriPrefix + ".first", null};
        }
    }
}
