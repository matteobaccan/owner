/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.ConverterClass;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.converters.DurationConverter;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the automatic conversion of {@link Duration}, and for how it differs from the
 * {@link DurationConverter} named explicitly with {@code @ConverterClass}: the automatic path requires
 * the time unit to be written, where the annotation keeps reading a bare number as milliseconds.
 *
 * @author Matteo Baccan
 */
public class AutomaticDurationConversionTest {

    interface Timeouts extends Config {
        Duration connect();

        Duration read();

        @DefaultValue("PT15M")
        Duration iso();

        Duration bare();

        Duration capitals();

        Duration nonsense();
    }

    private static Timeouts config() {
        return ConfigFactory.create(Timeouts.class, new Properties() {{
            setProperty("connect", "10 s");
            setProperty("read", "500 ms");
            setProperty("bare", "30");
            setProperty("capitals", "10 S");
            setProperty("nonsense", "abc");
        }});
    }

    // -------------------------------------------------------------------------------------------------
    // what works
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aDurationIsReadWithoutAnyAnnotation() {
        assertEquals(Duration.ofSeconds(10), config().connect());
        assertEquals(Duration.ofMillis(500), config().read());
    }

    @Test
    public void theIso8601FormIsAccepted() {
        assertEquals(Duration.ofMinutes(15), config().iso());
    }

    // -------------------------------------------------------------------------------------------------
    // what surprises
    // -------------------------------------------------------------------------------------------------

    /**
     * THE TRAP. A number with no unit is read as milliseconds. Whoever writes `timeout=30` meaning
     * seconds gets thirty milliseconds, silently, and their service times out immediately.
     */
    /**
     * Option (b): on the automatic path a bare number is refused, because it would silently mean
     * milliseconds. The message says what to write instead.
     */
    @Test
    public void aBareNumberIsRefusedOnTheAutomaticPath() {
        try {
            config().bare();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert '30' to a Duration for property 'bare': the time unit is "
                    + "missing. Write it explicitly — '30 ms', '30 s', '30 m', '30 h' or '30 d' — or use "
                    + "an ISO-8601 duration such as 'PT30S'. A bare number would be read as "
                    + "milliseconds, which is rarely what is meant.", e.getMessage());
        }
    }

    interface EveryAcceptedForm extends Config {
        @DefaultValue("10 s")     Duration spaced();
        @DefaultValue("10s")      Duration unspaced();
        @DefaultValue("500 ms")   Duration millis();
        @DefaultValue("10 seconds") Duration longForm();
        @DefaultValue("2 h")      Duration hours();
        @DefaultValue("1 d")      Duration days();
        @DefaultValue("PT15M")    Duration iso();
        @DefaultValue("-PT1H")    Duration negativeIso();
    }

    /** What "an explicit unit" accepts, spelled out. */
    @Test
    public void theFormsThatCountAsCarryingAUnit() {
        EveryAcceptedForm cfg = ConfigFactory.create(EveryAcceptedForm.class);

        assertEquals(Duration.ofSeconds(10), cfg.spaced());
        assertEquals(Duration.ofSeconds(10), cfg.unspaced());
        assertEquals(Duration.ofMillis(500), cfg.millis());
        assertEquals(Duration.ofSeconds(10), cfg.longForm());
        assertEquals(Duration.ofHours(2), cfg.hours());
        assertEquals(Duration.ofDays(1), cfg.days());
        assertEquals(Duration.ofMinutes(15), cfg.iso());
        assertEquals(Duration.ofHours(-1), cfg.negativeIso());
    }

    interface BareDefault extends Config {
        @DefaultValue("30")
        Duration fromDefault();
    }

    /** The rule applies to a @DefaultValue too: it goes through the same conversion. */
    @Test
    public void aBareNumberIsRefusedInADefaultValueAsWell() {
        try {
            ConfigFactory.create(BareDefault.class).fromDefault();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertEquals(true, e.getMessage().contains("the time unit is missing"));
        }
    }

    /** The lenient reading stays available where it was opted into. */
    @Test
    public void theAnnotationStillAcceptsABareNumberAsMilliseconds() {
        StillAnnotated cfg = ConfigFactory.create(StillAnnotated.class, new Properties() {{
            setProperty("explicit", "30");
        }});
        assertEquals(Duration.ofMillis(30), cfg.explicit());
    }

    /**
     * The unit is case sensitive, unlike the byte size units of this same library.
     */
    @Test
    public void theUnitIsCaseSensitive() {
        try {
            config().capitals();
            fail("'10 S' was rejected");
        } catch (RuntimeException e) {
            assertEquals(UnsupportedOperationException.class, e.getClass());
        }
    }

    @Test
    public void aValueThatDoesNotParseReportsLikeEveryOtherType() {
        try {
            config().nonsense();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertEquals("Cannot convert 'abc' to java.time.Duration for property 'nonsense'",
                    e.getMessage());
        }
    }

    // -------------------------------------------------------------------------------------------------
    // the escape hatches still work
    // -------------------------------------------------------------------------------------------------

    public static class AlwaysOneHour implements Converter<Duration> {
        @Override
        public Duration convert(Method method, String input) {
            return Duration.ofHours(1);
        }
    }

    interface WithExplicitConverter extends Config {
        @ConverterClass(AlwaysOneHour.class)
        Duration overridden();
    }

    @Test
    public void anExplicitConverterClassStillWins() {
        WithExplicitConverter cfg = ConfigFactory.create(WithExplicitConverter.class, new Properties() {{
            setProperty("overridden", "10 s");
        }});
        assertEquals(Duration.ofHours(1), cfg.overridden());
    }

    @Test
    public void aRegisteredConverterStillWins() {
        Factory factory = ConfigFactory.newInstance();
        factory.setTypeConverter(Duration.class, AlwaysOneHour.class);
        try {
            Timeouts cfg = factory.create(Timeouts.class, new Properties() {{
                setProperty("connect", "10 s");
            }});
            assertEquals(Duration.ofHours(1), cfg.connect());
        } finally {
            factory.removeTypeConverter(Duration.class);
        }
    }

    /**
     * And the annotation that was required until now keeps working, so nothing written against the
     * previous behaviour breaks.
     */
    interface StillAnnotated extends Config {
        @ConverterClass(DurationConverter.class)
        Duration explicit();
    }

    @Test
    public void theOldExplicitFormKeepsWorking() {
        StillAnnotated cfg = ConfigFactory.create(StillAnnotated.class, new Properties() {{
            setProperty("explicit", "2 h");
        }});
        assertEquals(Duration.ofHours(2), cfg.explicit());
    }

    // -------------------------------------------------------------------------------------------------
    // it behaves like any other converted type
    // -------------------------------------------------------------------------------------------------

    interface Composed extends Config {
        @DefaultValue("10 s, 20 s, 1 m")
        List<Duration> list();

        @DefaultValue("10 s, 20 s")
        Duration[] array();

        Optional<Duration> maybe();

        @DefaultValue("${slow}")
        Duration expanded();

        @DefaultValue("1 h")
        String slow();
    }

    @Test
    public void collectionsAndArraysOfDurationsAreConverted() {
        Composed cfg = ConfigFactory.create(Composed.class);

        assertEquals(asList(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofMinutes(1)),
                cfg.list());
        assertArrayEquals(new Duration[]{Duration.ofSeconds(10), Duration.ofSeconds(20)}, cfg.array());
    }

    @Test
    public void anOptionalDurationIsEmptyWhenThePropertyIsNotThere() {
        Composed cfg = ConfigFactory.create(Composed.class);
        assertFalse(cfg.maybe().isPresent());

        Composed set = ConfigFactory.create(Composed.class, new Properties() {{
            setProperty("maybe", "45 s");
        }});
        assertEquals(Optional.of(Duration.ofSeconds(45)), set.maybe());
    }

    @Test
    public void theValueGoesThroughVariableExpansionFirst() {
        assertEquals(Duration.ofHours(1), ConfigFactory.create(Composed.class).expanded());
    }

    interface Empty extends Config {
        Duration timeout();
    }

    /**
     * An empty value is a value like any other here too: it does not become a missing property, and a
     * duration cannot be made of it.
     */
    @Test
    public void anEmptyValueFailsTheConversion() {
        Empty cfg = ConfigFactory.create(Empty.class, new Properties() {{
            setProperty("timeout", "");
        }});
        try {
            cfg.timeout();
            fail("UnsupportedOperationException is expected");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("timeout"));
        }
    }

    @Test
    public void aMissingPropertyIsNullAsItIsForEveryOtherObjectType() {
        assertNull(ConfigFactory.create(Empty.class).timeout());
    }

    /**
     * The remaining accepted spellings of the ISO-8601 form, so that the check for it is exercised in
     * all three of its shapes.
     */
    @Test
    public void everyShapeOfTheIsoFormIsRecognised() {
        Iso cfg = ConfigFactory.create(Iso.class);
        assertEquals(Duration.ofSeconds(30), cfg.plain());
        assertEquals(Duration.ofSeconds(-30), cfg.negative());
        assertEquals(Duration.ofSeconds(30), cfg.positive());
    }

    interface Iso extends Config {
        @DefaultValue("PT30S")  Duration plain();
        @DefaultValue("-PT30S") Duration negative();
        @DefaultValue("+PT30S") Duration positive();
    }
}
