/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link DurationParser}, and above all for what it says when it refuses.
 *
 * @author Matteo Baccan
 */
public class DurationParserTest {

    /** Built from the code point: written as a character it would be indistinguishable from the next one. */
    private static final String GREEK_SMALL_LETTER_MU = new String(Character.toChars(0x03BC));
    private static final String MICRO_SIGN = new String(Character.toChars(0x00B5));

    @Test
    public void testIso8601IsRecognised() {
        assertTrue(DurationParser.isIso8601("PT30S"));
        assertTrue(DurationParser.isIso8601("-PT30S"));
        assertTrue(DurationParser.isIso8601("+PT30S"));
        assertFalse(DurationParser.isIso8601("30 s"));
    }

    @Test
    public void testAnIso8601DurationIsRead() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("PT30S"));
    }

    @Test
    public void testAValueAndUnitIsRead() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30 s"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5 minutes"));
        assertEquals(Duration.ofDays(2), DurationParser.parse("2d"));
    }

    @Test
    public void testABareNumberIsMilliseconds() {
        assertEquals(Duration.ofMillis(30), DurationParser.parse("30"));
    }

    @Test
    public void testTheMicroSignIsAccepted() {
        assertEquals(Duration.of(7, java.time.temporal.ChronoUnit.MICROS),
                DurationParser.parse("7 " + MICRO_SIGN + "s"));
    }

    /**
     * The two characters render alike, so refusing one of them with the ordinary "could not parse" message
     * leaves the reader staring at a unit that looks exactly right. The message has to name the difference.
     */
    @Test
    public void testTheGreekMuIsRefusedByName() {
        try {
            DurationParser.parse("7 " + GREEK_SMALL_LETTER_MU + "s");
            fail("the Greek mu is not the micro sign and should not be read as one");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("U+03BC"));
            assertTrue(message, message.contains("U+00B5"));
            assertTrue(message, message.contains("'us'"));
        }
    }

    @Test
    public void testTheGreekMuOnItsOwnIsRefusedTheSameWay() {
        try {
            DurationParser.parse("7 " + GREEK_SMALL_LETTER_MU);
            fail("a lone Greek mu is not a unit either");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("U+03BC"));
        }
    }

    @Test
    public void testAnOrdinaryUnknownUnitKeepsTheOrdinaryMessage() {
        try {
            DurationParser.parse("7 fortnights");
            fail("an unknown unit should be refused");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("fortnights"));
            assertTrue(message, message.contains("try ns, us, ms, s, m, h, d"));
            assertFalse(message, message.contains("U+03BC"));
        }
    }

    /**
     * The separator between the digits and the unit is where a copied-in no-break space lands, and it is
     * both undetectable on screen and untouched by {@link String#trim()}. Left to {@code Long.parseLong} the
     * message quotes a value that looks perfectly correct.
     */
    @Test
    public void testANoBreakSpaceIsNamed() {
        try {
            DurationParser.parse("30" + new String(Character.toChars(0x00A0)) + "s");
            fail("a no-break space is not a space and should be reported as such");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("NO-BREAK SPACE"));
            assertTrue(message, message.contains("U+00A0"));
            assertTrue(message, message.contains("position 2"));
        }
    }

    @Test
    public void testAZeroWidthSpaceIsNamedToo() {
        try {
            DurationParser.parse("30" + new String(Character.toChars(0x200B)) + " s");
            fail("a zero width space should be reported");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("U+200B"));
        }
    }

    @Test
    public void testANarrowNoBreakSpaceIsNamedToo() {
        try {
            DurationParser.parse("30" + new String(Character.toChars(0x202F)) + "s");
            fail("a narrow no-break space should be reported");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("U+202F"));
        }
    }

    @Test
    public void testAnOrdinarySpaceIsStillJustASeparator() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30 s"));
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30\ts"));
    }

    @Test
    public void testANumberThatIsWrongForAnotherReasonKeepsTheOrdinaryMessage() {
        try {
            DurationParser.parse("1.5 s");
            fail("a fractional amount is not accepted");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("Could not read the number"));
            assertFalse(message, message.contains("reads as a space"));
        }
    }

    @Test
    public void testAValueWithNoNumberIsRefused() {
        try {
            DurationParser.parse("seconds");
            fail("a duration with no number should be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("No number"));
        }
    }
}
