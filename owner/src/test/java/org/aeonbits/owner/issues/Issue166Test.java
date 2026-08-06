/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * See: https://github.com/lviggiano/owner/issues/166
 * <p>
 * A key can be composed out of a variable, which is what "dynamic key names" comes down to: the name lives in
 * the mapping interface, where it is built, and not in the properties file, whose keys are plain text. The
 * shape asked for there — <code>${abc}One</code>, a variable glued to a literal with no separator between them
 * — is covered here, since the documented examples all separate the two with a dot.
 */
public class Issue166Test {

    interface DynamicKeyConfig extends Config {
        @Key("${abc}One")
        String value();
    }

    @Test
    public void aKeyCanStartWithAVariableGluedToTheRestOfIt() {
        assertEquals("raja", ConfigFactory.create(DynamicKeyConfig.class, new Properties() {{
            setProperty("abc", "xyz");
            setProperty("xyzOne", "raja");
        }}).value());
    }

    @Test
    public void theVariableChoosesWhichPropertyIsRead() {
        Properties values = new Properties() {{
            setProperty("xyzOne", "raja");
            setProperty("abcOne", "someone else");
        }};

        values.setProperty("abc", "xyz");
        assertEquals("raja", ConfigFactory.create(DynamicKeyConfig.class, values).value());

        values.setProperty("abc", "abc");
        assertEquals("someone else", ConfigFactory.create(DynamicKeyConfig.class, values).value());
    }

    interface ComposedKeyConfig extends Config {
        @Key("${owner}${separator}${name}")
        String value();
    }

    /** Nothing limits a key to a single variable, or to having any literal text at all. */
    @Test
    public void aKeyCanBeBuiltEntirelyOutOfVariables() {
        assertEquals("composed", ConfigFactory.create(ComposedKeyConfig.class, new Properties() {{
            setProperty("owner", "db");
            setProperty("separator", ".");
            setProperty("name", "url");
            setProperty("db.url", "composed");
        }}).value());
    }
}
