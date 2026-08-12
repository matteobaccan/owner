/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Types built by a public static factory - <code>of(String)</code> or <code>parse(CharSequence)</code> -
 * which is how every <code>java.time</code> type is built and which nothing here could read before 2.0.0.
 *
 * <p>
 * The conversion chain used to end at a <code>String</code> constructor, a <code>valueOf(String)</code> and
 * an <code>Object</code> constructor. {@link LocalDate} and its relatives have none of the three, so they
 * reached the end of the chain and were refused. MicroProfile Config settled the naming question for the
 * whole ecosystem - its implicit converters are <code>of</code>, <code>valueOf</code>, <code>parse</code>
 * and the <code>String</code> constructor - and these two links are the ones we were missing.
 * </p>
 *
 * @author Matteo Baccan
 */
public class StaticFactoryConversionTest {

    public interface DateTimeConfig extends Config {
        @DefaultValue("2026-08-12")
        LocalDate date();

        @DefaultValue("07:32:00")
        LocalTime time();

        @DefaultValue("2026-08-12T07:32:00")
        LocalDateTime dateTime();

        @DefaultValue("1979-05-27T07:32:00Z")
        OffsetDateTime offsetDateTime();

        @DefaultValue("2026")
        Year year();

        @DefaultValue("Europe/Rome")
        ZoneId zone();
    }

    private final DateTimeConfig cfg = ConfigFactory.create(DateTimeConfig.class);

    @Test
    public void theFourDateAndTimeTypesTomlHasAreRead() {
        assertEquals(LocalDate.of(2026, 8, 12), cfg.date());
        assertEquals(LocalTime.of(7, 32), cfg.time());
        assertEquals(LocalDateTime.of(2026, 8, 12, 7, 32), cfg.dateTime());
        assertEquals(OffsetDateTime.parse("1979-05-27T07:32:00Z"), cfg.offsetDateTime());
    }

    @Test
    public void aTypeWithParseButAlsoOneWithOf() {
        // Year has parse(CharSequence); ZoneId has of(String) and neither a constructor nor valueOf
        assertEquals(Year.of(2026), cfg.year());
        assertEquals(ZoneId.of("Europe/Rome"), cfg.zone());
    }

    public interface ListOfDatesConfig extends Config {
        @DefaultValue("2026-01-01, 2026-06-15, 2026-12-31")
        List<LocalDate> dates();
    }

    @Test
    public void theyTokenizeIntoCollectionsLikeAnyOtherType() {
        List<LocalDate> dates = ConfigFactory.create(ListOfDatesConfig.class).dates();
        assertEquals(3, dates.size());
        assertEquals(LocalDate.of(2026, 6, 15), dates.get(1));
    }

    public interface BadDateConfig extends Config {
        @DefaultValue("the thirty-first of never")
        LocalDate date();
    }

    @Test
    public void aValueTheFactoryRefusesSaysWhySpecifically() {
        try {
            ConfigFactory.create(BadDateConfig.class).date();
            fail("a value that is not a date was expected to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("the thirty-first of never"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("LocalDate"));
            // the factory said no, and what it said is kept: skipping to the next link would have ended in
            // the same refusal with nothing under it
            Throwable cause = refused.getCause();
            while (cause != null && !(cause instanceof java.time.format.DateTimeParseException))
                cause = cause.getCause();
            assertTrue("the DateTimeParseException was lost", cause != null);
        }
    }

    /** A factory that is not static must not be called, and must not stop the chain either. */
    public static class NotAFactory {
        private final String text;

        public NotAFactory(String text) {
            this.text = text;
        }

        @SuppressWarnings("unused")
        public NotAFactory parse(CharSequence ignored) {
            throw new AssertionError("an instance method named parse must not be used as a factory");
        }

        String text() {
            return text;
        }
    }

    public interface NotAFactoryConfig extends Config {
        @DefaultValue("built by the constructor")
        NotAFactory value();
    }

    @Test
    public void anInstanceMethodNamedParseIsNotAFactory() {
        assertEquals("built by the constructor",
                ConfigFactory.create(NotAFactoryConfig.class).value().text());
    }
    // ------------------------------- the ways a factory can be there and unusable

    /**
     * A type whose <code>of</code> is not static. It has no <code>String</code> constructor and no
     * <code>valueOf</code> either, so the chain really does reach the factory links and has to decide that
     * this is not one of them rather than call it on nothing.
     */
    public static class InstanceFactoryOnly {
        @SuppressWarnings("unused")
        public InstanceFactoryOnly of(String text) {
            throw new AssertionError("an instance method is not a static factory");
        }
    }

    public interface InstanceFactoryConfig extends Config {
        @DefaultValue("anything")
        InstanceFactoryOnly value();
    }

    @Test
    public void aFactoryThatIsNotStaticIsNotAFactory() {
        try {
            ConfigFactory.create(InstanceFactoryConfig.class).value();
            fail("a type with only an instance of() was expected to be refused");
        } catch (UnsupportedOperationException refused) {
            assertTrue(refused.getMessage(), refused.getMessage().contains("Cannot convert"));
        }
    }

    // Two more of Converters' uncovered lines were tried and are left alone, with what was learnt.
    //
    // A public static factory on a NON-public type would exercise the IllegalAccessException arm, but the
    // proxy cannot return such a type at all: the failure is an IllegalAccessError from the proxy itself,
    // before any conversion is attempted, so that arm cannot be reached from a configuration interface.
    //
    // A method returning List<String>[] would exercise the GenericArrayType arm of elementType - and it
    // does not merely fail, it ends in a StackOverflowError: elementType erases the component to List,
    // ARRAY hands that to COLLECTION, and COLLECTION asks elementType about the same method again. That is
    // a defect rather than a coverage gap and it is recorded in TODO.md; a test asserting the overflow
    // would only fix the bug in place.
}
