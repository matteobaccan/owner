/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The paths providers actually build, kept apart from the providers that build them: the shapes below are
 * copied from what Hibernate Validator and Apache BVal answered, and they differ in the node names and agree
 * on the brackets, which is why only the brackets are read.
 *
 * @author Matteo Baccan
 */
public class ViolationPathTest {

    @Test
    public void aPlainValueAddsNothingToItsMessage() {
        assertEquals("must be at least 12",
                ViolationPath.describe("port.<return value>", "port", "must be at least 12"));
    }

    @Test
    public void anElementOfAListIsNamed() {
        assertEquals("element [1]: must be at least 12",
                ViolationPath.describe("ports.<return value>[1].<list element>", "ports",
                        "must be at least 12"));
    }

    /** A map is keyed by name rather than by index, and the key is what the reader has to go and find. */
    @Test
    public void anEntryOfAMapIsNamedByItsKey() {
        assertEquals("element [alpha]: must not be null",
                ViolationPath.describe("servers.<return value>[alpha].<map value>", "servers",
                        "must not be null"));
    }

    @Test
    public void aPathThatSaysNothingIsNotInvented() {
        assertEquals("must not be null", ViolationPath.describe(null, "hostname", "must not be null"));
        assertEquals("must not be null", ViolationPath.describe("", "hostname", "must not be null"));
    }
}
