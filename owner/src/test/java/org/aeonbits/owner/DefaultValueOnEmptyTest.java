/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link Config.DefaultValue#useOnEmpty()}: a property that is there but empty is normally a value
 * like any other, and only a method that opts in has it covered by the default.
 * <p>
 * The line the flag must not cross is a value that is <em>wrong</em> rather than empty: falling back there
 * would turn <code>port=8O80</code>, written with the letter O, into a silent 8080.
 *
 * @author Matteo Baccan
 */
public class DefaultValueOnEmptyTest {

    public interface StrictConfig extends Config {
        @Key("server.port")
        @DefaultValue("8080")
        int port();

        @Key("server.name")
        @DefaultValue("localhost")
        String name();

        @Key("server.hosts")
        @DefaultValue("alpha, beta")
        List<String> hosts();
    }

    public interface LenientConfig extends Config, Accessible {
        @Key("server.port")
        @DefaultValue(value = "8080", useOnEmpty = true)
        int port();

        @Key("server.name")
        @DefaultValue(value = "localhost", useOnEmpty = true)
        String name();

        @Key("server.hosts")
        @DefaultValue(value = "alpha, beta", useOnEmpty = true)
        List<String> hosts();

        @Key("server.fallback")
        @DefaultValue(value = "${fallback.port}", useOnEmpty = true)
        int fallbackPort();
    }

    private static Map<String, String> given(String... keysAndValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2)
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    @Test
    public void withoutTheFlagAnEmptyValueStillFails() {
        StrictConfig cfg = ConfigFactory.create(StrictConfig.class, given("server.port", ""));

        try {
            cfg.port();
            fail("an UnsupportedOperationException was expected");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert '' to int for property 'server.port'", e.getMessage());
        }
    }

    @Test
    public void withTheFlagAnEmptyValueIsCoveredByTheDefault() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", ""));

        assertEquals(8080, cfg.port());
    }

    /** Whitespace is empty as well, which is the rule an empty collection already follows. */
    @Test
    public void aValueMadeOfWhitespaceCountsAsEmpty() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", "   "));

        assertEquals(8080, cfg.port());
    }

    /**
     * The case the flag exists for: a template that nobody filled in. The variable is expanded first, so what
     * reaches the conversion is empty even though the property is not.
     */
    @Test
    public void aTemplateLeftUnfilledIsEmptyToo() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", "${PORT}"));

        assertEquals(8080, cfg.port());
    }

    /** The whole point of not doing this by default: a wrong value is not an empty one. */
    @Test
    public void aValueThatIsWrongRatherThanEmptyStillFails() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", "8O80"));

        try {
            cfg.port();
            fail("the default value was used in place of a typo");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert '8O80' to int for property 'server.port'", e.getMessage());
        }
    }

    @Test
    public void aValueThatIsSetIsLeftAlone() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", "9090"));

        assertEquals(9090, cfg.port());
    }

    @Test
    public void aMissingPropertyKeepsUsingTheDefaultAsBefore() {
        assertEquals(8080, ConfigFactory.create(LenientConfig.class).port());
        assertEquals(8080, ConfigFactory.create(StrictConfig.class).port());
    }

    /**
     * The types where the empty value is already meaningful change behaviour too, which is why the flag is
     * opt-in: without it a String stays empty and a list stays empty.
     */
    @Test
    public void theTypesThatAcceptAnEmptyValueAreCoveredAsWell() {
        Map<String, String> empty = given("server.name", "", "server.hosts", "");

        StrictConfig strict = ConfigFactory.create(StrictConfig.class, empty);
        assertEquals("", strict.name());
        assertTrue(strict.hosts().isEmpty());

        LenientConfig lenient = ConfigFactory.create(LenientConfig.class, empty);
        assertEquals("localhost", lenient.name());
        assertEquals(Arrays.asList("alpha", "beta"), lenient.hosts());
    }

    /**
     * The default replacing an empty value goes through the same steps the value would have gone through, so
     * it can be a variable itself, exactly as it can be when the property is missing altogether.
     */
    @Test
    public void theDefaultUsedOnAnEmptyValueIsExpanded() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class,
                given("server.fallback", "", "fallback.port", "7070"));

        assertEquals(7070, cfg.fallbackPort());
    }

    /**
     * The substitution happens on the value returned by the method: what is stored stays what was configured,
     * which is the same split that variable expansion already has.
     */
    @Test
    public void thePropertiesThemselvesKeepTheEmptyValue() {
        LenientConfig cfg = ConfigFactory.create(LenientConfig.class, given("server.port", ""));

        assertEquals(8080, cfg.port());
        assertEquals("", cfg.getProperty("server.port"));
    }
}
