/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.junit.After;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * See: https://github.com/lviggiano/owner/issues/317
 * <p>
 * A variable used in {@link Sources} can carry a default value since 2.0.0, so the source to load can be
 * selected at runtime and fall back to a fixed one. The syntax is <code>${env:develop}</code>: everything after
 * the <em>first</em> colon is the default, colons included, which is what keeps URLs and Windows paths usable as
 * defaults — and which is why the shell form <code>${env:-develop}</code> is not recognised, its dash being read
 * as the first character of the default.
 */
public class Issue317Test {

    // first.properties -> foo=first, baz=first ; second.properties -> foo=second, bar=second

    @Sources("classpath:org/aeonbits/owner/${env:first}.properties")
    interface SelectedByEnv extends Config {
        String foo();
    }

    @After
    public void clearEnv() {
        ConfigFactory.clearProperty("env");
    }

    @Test
    public void theDefaultSelectsTheSourceWhenTheVariableIsNotSet() {
        assertEquals("first", ConfigFactory.create(SelectedByEnv.class).foo());
    }

    @Test
    public void theVariableOverridesTheDefault() {
        ConfigFactory.setProperty("env", "second");
        assertEquals("second", ConfigFactory.create(SelectedByEnv.class).foo());
    }

    @Sources("classpath:org/aeonbits/owner/${env:-develop}.properties")
    interface SelectedByShellSyntax extends Config {
        String foo();
    }

    /**
     * The shell form is a trap rather than an alternative: it resolves correctly whenever the variable is set,
     * and falls back to a source named <code>-develop</code> when it is not. Pinned so that the behaviour is a
     * documented consequence of the "first colon" rule and not an accident.
     */
    @Test
    public void theShellFormKeepsTheDashAsPartOfTheDefault() {
        assertNull(ConfigFactory.create(SelectedByShellSyntax.class).foo());

        ConfigFactory.setProperty("env", "second");
        assertEquals("second", ConfigFactory.create(SelectedByShellSyntax.class).foo());
    }

    interface NegativeDefault extends Config {
        @Key("configured.offset")
        @DefaultValue("${offset:-1}")
        int offset();
    }

    /**
     * The reason the dash cannot simply be swallowed to support the shell form: a negative number is a
     * legitimate default, and <code>${offset:-1}</code> has to keep meaning "minus one" rather than "one".
     */
    @Test
    public void aNegativeNumberIsALegitimateDefault() {
        assertEquals(-1, ConfigFactory.create(NegativeDefault.class).offset());

        assertEquals(5, ConfigFactory.create(NegativeDefault.class, new Properties() {{
            setProperty("offset", "5");
        }}).offset());
    }
}
