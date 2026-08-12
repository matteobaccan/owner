/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.loaders.PropertiesLoader;
import org.aeonbits.owner.loaders.PropertyKeys;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The guards that never fired.
 *
 * <p>
 * Each of these is one line of a class that is otherwise fully exercised, and each is a refusal or a
 * fallback rather than a feature — which is why the suite reached everything around them and never them.
 * They are worth a test apiece for the same reason a refusal in a parser is: a guard that has never been
 * executed is a guard nobody has watched work, and the day it fires is the day somebody is already
 * confused.
 * </p>
 *
 * @author Matteo Baccan
 */
public class SmallGuardsTest {

    @Test
    public void anElementIndexCannotBeNegative() {
        // the convention is public and a loader of somebody else's may call it with whatever it computed
        try {
            PropertyKeys.element("servers", -1);
            fail("a negative index was expected to be refused");
        } catch (IllegalArgumentException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("cannot be negative"));
            assertTrue("the index it was given belongs in the message: " + refused.getMessage(),
                    refused.getMessage().contains("-1"));
        }
    }

    @Test
    public void anIndexOfZeroIsFine() {
        assertEquals("servers[0]", PropertyKeys.element("servers", 0));
    }

    @Test
    public void theFallbackLoaderRefusesASourceWithNoSchemeInsteadOfThrowing() {
        // a spec whose ${...} did not expand produces one of these, and PropertiesLoader accepts anything
        // it can resolve - so if it threw here the search for a loader would stop rather than move on
        assertFalse(new PropertiesLoader().accept(URI.create("myconfig.properties")));
        assertFalse(new PropertiesLoader().accept(URI.create("")));
    }

    @Test
    public void theFallbackLoaderStillAcceptsWhatItCanResolve() {
        assertTrue(new PropertiesLoader().accept(URI.create("file:/etc/app.properties")));
    }

    @Test
    public void aNestedPrefixDescribesItselfAsAPath() {
        // the one prefix written nowhere in the source, so the diagnostics have to name it: a section
        // reached through a nested interface carries the path it hangs from
        assertEquals("the path 'server'", KeyPrefix.nestedIn("server").describe());
    }

    @Test
    public void aPrefixThatIsNotThereDescribesItselfAsNothing() {
        assertEquals(null, KeyPrefix.NONE.describe());
    }
}
