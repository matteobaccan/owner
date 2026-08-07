/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util.bytesize;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link ByteSizeUnit}.
 *
 * @author Matteo Baccan
 */
public class ByteSizeUnitTest {

    @Test
    public void testParseLongAndShortFormsForAllUnits() {
        for (ByteSizeUnit unit : ByteSizeUnit.values()) {
            // long plural form, e.g. "kilobytes"
            assertEquals(unit, ByteSizeUnit.parse(unit.toStringLongForm()));
            // short form is case insensitive, e.g. "KiB", "KB", "B"
            assertEquals(unit, ByteSizeUnit.parse(unit.toStringShortForm()));
            assertEquals(unit, ByteSizeUnit.parse(unit.toStringShortForm().toUpperCase()));
        }
    }

    @Test
    public void testParseByteForms() {
        assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse(""));
        assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse("b"));
        assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse("B"));
        assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse("byte"));
        assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse("bytes"));
    }

    @Test
    public void testParseSiForms() {
        assertEquals(ByteSizeUnit.KILOBYTES, ByteSizeUnit.parse("kb"));
        assertEquals(ByteSizeUnit.KILOBYTES, ByteSizeUnit.parse("kilobyte"));
        assertEquals(ByteSizeUnit.KILOBYTES, ByteSizeUnit.parse("kilobytes"));
        assertEquals(ByteSizeUnit.MEGABYTES, ByteSizeUnit.parse("MB"));
        assertEquals(ByteSizeUnit.GIGABYTES, ByteSizeUnit.parse("gigabytes"));
        assertEquals(ByteSizeUnit.TERABYTES, ByteSizeUnit.parse("tb"));
        assertEquals(ByteSizeUnit.PETABYTES, ByteSizeUnit.parse("petabyte"));
        assertEquals(ByteSizeUnit.EXABYTES, ByteSizeUnit.parse("eb"));
        assertEquals(ByteSizeUnit.ZETTABYTES, ByteSizeUnit.parse("zb"));
        assertEquals(ByteSizeUnit.YOTTABYTES, ByteSizeUnit.parse("yb"));
    }

    @Test
    public void testParseIecForms() {
        // single letter, [first]i and [first]ib all map to the IEC unit
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("k"));
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("ki"));
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("kib"));
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("kibibyte"));
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("kibibytes"));
        assertEquals(ByteSizeUnit.MEBIBYTES, ByteSizeUnit.parse("m"));
        assertEquals(ByteSizeUnit.MEBIBYTES, ByteSizeUnit.parse("MiB"));
        assertEquals(ByteSizeUnit.GIBIBYTES, ByteSizeUnit.parse("g"));
        assertEquals(ByteSizeUnit.TEBIBYTES, ByteSizeUnit.parse("t"));
        assertEquals(ByteSizeUnit.PEBIBYTES, ByteSizeUnit.parse("p"));
        assertEquals(ByteSizeUnit.EXBIBYTES, ByteSizeUnit.parse("e"));
        assertEquals(ByteSizeUnit.ZEBIBYTES, ByteSizeUnit.parse("z"));
        assertEquals(ByteSizeUnit.YOBIBYTES, ByteSizeUnit.parse("y"));
    }

    @Test
    public void testParseIsCaseInsensitive() {
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("KIB"));
        assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("kIb"));
        assertEquals(ByteSizeUnit.MEGABYTES, ByteSizeUnit.parse("MeGaByTeS"));
    }

    @Test
    public void testParseReturnsNullOnUnknownUnit() {
        assertNull(ByteSizeUnit.parse("banana"));
        assertNull(ByteSizeUnit.parse("kilob"));
        assertNull(ByteSizeUnit.parse("x"));
        assertNull(ByteSizeUnit.parse("bib"));
    }

    @Test
    public void testIsSIAndIsIEC() {
        assertTrue(ByteSizeUnit.BYTES.isSI());
        assertFalse(ByteSizeUnit.BYTES.isIEC());

        assertTrue(ByteSizeUnit.KILOBYTES.isSI());
        assertFalse(ByteSizeUnit.KILOBYTES.isIEC());

        assertTrue(ByteSizeUnit.KIBIBYTES.isIEC());
        assertFalse(ByteSizeUnit.KIBIBYTES.isSI());

        for (ByteSizeUnit unit : ByteSizeUnit.values()) {
            // every unit belongs to exactly one standard
            assertTrue(unit.isSI() != unit.isIEC());
        }
    }

    @Test
    public void testGetFactor() {
        assertEquals(BigDecimal.ONE, ByteSizeUnit.BYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1000), ByteSizeUnit.KILOBYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1024), ByteSizeUnit.KIBIBYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1000).pow(2), ByteSizeUnit.MEGABYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1024).pow(2), ByteSizeUnit.MEBIBYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1000).pow(8), ByteSizeUnit.YOTTABYTES.getFactor());
        assertEquals(BigDecimal.valueOf(1024).pow(8), ByteSizeUnit.YOBIBYTES.getFactor());
    }

    @Test
    public void testToStringLongForm() {
        assertEquals("bytes", ByteSizeUnit.BYTES.toStringLongForm());
        assertEquals("kilobytes", ByteSizeUnit.KILOBYTES.toStringLongForm());
        assertEquals("kibibytes", ByteSizeUnit.KIBIBYTES.toStringLongForm());
        assertEquals("megabytes", ByteSizeUnit.MEGABYTES.toStringLongForm());
        assertEquals("yobibytes", ByteSizeUnit.YOBIBYTES.toStringLongForm());
    }

    @Test
    public void testToStringShortForm() {
        assertEquals("B", ByteSizeUnit.BYTES.toStringShortForm());
        assertEquals("KB", ByteSizeUnit.KILOBYTES.toStringShortForm());
        assertEquals("KiB", ByteSizeUnit.KIBIBYTES.toStringShortForm());
        assertEquals("MB", ByteSizeUnit.MEGABYTES.toStringShortForm());
        assertEquals("YiB", ByteSizeUnit.YOBIBYTES.toStringShortForm());
    }

    // -------------------------------------------------------------------------------------------------
    // parsing does not depend on where the JVM thinks it is running
    // -------------------------------------------------------------------------------------------------

    /**
     * Lowercasing without saying in which language is the one mistake that turns a working configuration
     * into a broken one depending on the machine it runs on. In Turkish the capital <code>I</code>
     * lowercases to the dotless <code>ı</code>, so <code>"KIB"</code> became <code>"kıb"</code>, matched no
     * unit, and a legitimate <code>512 KIB</code> was rejected as an invalid unit — on a Turkish JVM only.
     * Every IEC unit written in capitals was affected, since all of them carry an <code>i</code>.
     */
    @Test
    public void parseIsNotAffectedByTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("KIB"));
            assertEquals(ByteSizeUnit.MEBIBYTES, ByteSizeUnit.parse("MIB"));
            assertEquals(ByteSizeUnit.GIBIBYTES, ByteSizeUnit.parse("GIB"));
            assertEquals(ByteSizeUnit.KIBIBYTES, ByteSizeUnit.parse("KIBIBYTES"));
            assertEquals(ByteSizeUnit.MEBIBYTES, ByteSizeUnit.parse("MEBIBYTE"));

            // the SI units carry no i, but they are the control group
            assertEquals(ByteSizeUnit.KILOBYTES, ByteSizeUnit.parse("KB"));
            assertEquals(ByteSizeUnit.BYTES, ByteSizeUnit.parse("B"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void aByteSizeIsReadTheSameWayInAnyLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(new ByteSize(512, ByteSizeUnit.KIBIBYTES),
                    new ByteSize(512, ByteSizeUnit.parse("KIB")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void parseRejectsNullSayingSo() {
        try {
            ByteSizeUnit.parse(null);
            fail("NullPointerException is expected");
        } catch (NullPointerException e) {
            assertNotNull("the message should say what was null", e.getMessage());
            assertTrue(e.getMessage().toLowerCase().contains("unit"));
        }
    }
}
