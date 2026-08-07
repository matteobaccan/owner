/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util.bytesize;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class ByteSizeTest {
    @Test
    public void testBasics(){
        assertEquals(1, new ByteSize(1, ByteSizeUnit.BYTES).getBytesAsLong());

        BigInteger siBytes = BigInteger.valueOf(1000);
        BigInteger iecBytes = BigInteger.valueOf(1024);

        for (ByteSizeUnit bsu : ByteSizeUnit.values()) {
            if (bsu == ByteSizeUnit.BYTES) {
                assertEquals(1, new ByteSize(1, bsu).getBytesAsLong());
            } else if (bsu.isIEC()) {
                assertEquals(iecBytes, new ByteSize(1, bsu).getBytes());
                iecBytes = iecBytes.multiply(BigInteger.valueOf(1024));
            } else if (bsu.isSI()) {
                assertEquals(siBytes, new ByteSize(1, bsu).getBytes());
                siBytes = siBytes.multiply(BigInteger.valueOf(1000));
            }
        }
    }

    @Test
    public void testConversion(){
        assertEquals(new ByteSize(0.5, ByteSizeUnit.GIGABYTES), new ByteSize(500, ByteSizeUnit.MEGABYTES).convertTo(ByteSizeUnit.GIGABYTES));
        assertEquals(new ByteSize(9.765625, ByteSizeUnit.KIBIBYTES), new ByteSize(10, ByteSizeUnit.KILOBYTES).convertTo(ByteSizeUnit.KIBIBYTES));
        assertEquals(new ByteSize(10, ByteSizeUnit.MEGABYTES), new ByteSize(10, ByteSizeUnit.MEGABYTES).convertTo(ByteSizeUnit.MEGABYTES));
        ByteSize bs = new ByteSize(1, ByteSizeUnit.BYTES).convertTo(ByteSizeUnit.ZETTABYTES);
        assertEquals(1, bs.getBytesAsLong());
        assertEquals(new ByteSize(1, ByteSizeUnit.BYTES), bs.convertTo(ByteSizeUnit.BYTES));
    }

    @Test
    public void testEquality(){
        assertEquals(new ByteSize(500, ByteSizeUnit.MEGABYTES), new ByteSize(0.5, ByteSizeUnit.GIGABYTES));
        assertEquals(new ByteSize(500, ByteSizeUnit.MEBIBYTES), new ByteSize("0.48828125", ByteSizeUnit.GIBIBYTES));
    }

    @Test
    public void testLongConstructor(){
        ByteSize bs = new ByteSize(1024L);
        assertEquals(1024L, bs.getBytesAsLong());
        assertEquals(new ByteSize(1, ByteSizeUnit.KIBIBYTES), bs);
    }

    @Test
    public void testGetBytesAsInt(){
        assertEquals(2048, new ByteSize(2, ByteSizeUnit.KIBIBYTES).getBytesAsInt());
        assertEquals(42, new ByteSize(42L).getBytesAsInt());
    }

    @Test
    public void testGetBytesAsIntOverflow(){
        try {
            new ByteSize(1, ByteSizeUnit.TERABYTES).getBytesAsInt();
            fail("expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // a terabyte does not fit in an int
        }
    }

    @Test
    public void testToString(){
        assertEquals("10 B", new ByteSize(10L).toString());
        assertEquals("1.5 MB", new ByteSize(1.5, ByteSizeUnit.MEGABYTES).toString());
        assertEquals("512 KiB", new ByteSize("512", ByteSizeUnit.KIBIBYTES).toString());
    }

    @Test
    public void testEqualsSpecialCases(){
        ByteSize bs = new ByteSize(1, ByteSizeUnit.MEGABYTES);
        assertEquals(bs, bs);
        assertNotEquals(bs, null);
        assertNotEquals(bs, "1 MB");
        assertNotEquals(bs, new ByteSize(2, ByteSizeUnit.MEGABYTES));
    }

    @Test
    public void testHashCode(){
        ByteSize first = new ByteSize(1, ByteSizeUnit.MEGABYTES);
        ByteSize second = new ByteSize(1, ByteSizeUnit.MEGABYTES);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.hashCode(), first.hashCode());
    }

    // -------------------------------------------------------------------------------------------------
    // the equals/hashCode contract
    //
    // equals compares the number of bytes, so two instances written in different units, or with a
    // different scale, are equal. Every one of those pairs must therefore agree on the hash code: the
    // test above only ever compared instances built the same way, which is why it kept passing while
    // the contract was broken.
    // -------------------------------------------------------------------------------------------------

    @Test
    public void equalSizesWrittenInDifferentUnitsShareTheHashCode(){
        ByteSize oneMegabyte = new ByteSize(1, ByteSizeUnit.MEGABYTES);
        ByteSize aMillionBytes = new ByteSize(1000000L, ByteSizeUnit.BYTES);

        assertEquals(oneMegabyte, aMillionBytes);
        assertEquals("equal objects must have equal hash codes",
                oneMegabyte.hashCode(), aMillionBytes.hashCode());
    }

    @Test
    public void equalSizesWrittenWithADifferentScaleShareTheHashCode(){
        ByteSize withDecimals = new ByteSize("1.0", ByteSizeUnit.BYTES);
        ByteSize withoutDecimals = new ByteSize("1", ByteSizeUnit.BYTES);

        assertEquals(withDecimals, withoutDecimals);
        assertEquals("equal objects must have equal hash codes",
                withDecimals.hashCode(), withoutDecimals.hashCode());
    }

    @Test
    public void aByteSizeWorksAsAKeyOfAHashMap(){
        Map<ByteSize, String> map = new HashMap<>();
        map.put(new ByteSize(1, ByteSizeUnit.MEGABYTES), "a megabyte");

        assertEquals("a megabyte", map.get(new ByteSize(1000000L, ByteSizeUnit.BYTES)));
        assertEquals("a megabyte", map.get(new ByteSize(1, ByteSizeUnit.MEGABYTES)));
        assertNull(map.get(new ByteSize(1, ByteSizeUnit.MEBIBYTES)));
    }

    @Test
    public void aSetHoldsOneEntryPerSizeRatherThanPerSpelling(){
        Set<ByteSize> sizes = new HashSet<>();
        sizes.add(new ByteSize(1, ByteSizeUnit.MEGABYTES));
        sizes.add(new ByteSize(1000000L, ByteSizeUnit.BYTES));
        sizes.add(new ByteSize("1000.0", ByteSizeUnit.KILOBYTES));

        assertEquals(1, sizes.size());
    }

    @Test
    public void differentSizesAreNotEqual(){
        assertNotEquals(new ByteSize(1, ByteSizeUnit.MEGABYTES), new ByteSize(1, ByteSizeUnit.MEBIBYTES));
        assertNotEquals(new ByteSize(1, ByteSizeUnit.MEGABYTES).hashCode(),
                new ByteSize(1, ByteSizeUnit.MEBIBYTES).hashCode());
    }

    // -------------------------------------------------------------------------------------------------
    // the type cannot be built in a broken state, nor extended into one
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theTypeIsFinal(){
        assertTrue("a value type has to be final, or equals() cannot hold across subclasses",
                Modifier.isFinal(ByteSize.class.getModifiers()));
    }

    @Test
    public void everyFieldIsFinal(){
        for (Field field : ByteSize.class.getDeclaredFields())
            assertTrue("the field '" + field.getName() + "' is not final",
                    Modifier.isFinal(field.getModifiers()));
    }

    @Test
    public void aNullValueIsRejectedWhereItIsWrittenRatherThanWhereItIsUsed(){
        try {
            new ByteSize((BigDecimal) null, ByteSizeUnit.BYTES);
            fail("NullPointerException is expected");
        } catch (NullPointerException e) {
            assertNotNull("the message should say which part is missing", e.getMessage());
            assertTrue(e.getMessage().toLowerCase().contains("value"));
        }
    }

    @Test
    public void aNullUnitIsRejectedWhereItIsWrittenRatherThanWhereItIsUsed(){
        try {
            new ByteSize(1L, null);
            fail("NullPointerException is expected");
        } catch (NullPointerException e) {
            assertNotNull("the message should say which part is missing", e.getMessage());
            assertTrue(e.getMessage().toLowerCase().contains("unit"));
        }
    }

    @Test
    public void aNullTextIsRejectedToo(){
        try {
            new ByteSize((String) null, ByteSizeUnit.BYTES);
            fail("NullPointerException is expected");
        } catch (NullPointerException e) {
            assertNotNull("the message should say which part is missing", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------------------------------
    // what the arithmetic actually does, written down
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aFractionOfAByteIsRoundedUp(){
        // documented: the rounding never gives a value that is too small
        assertEquals(BigInteger.valueOf(2), new ByteSize("1.5", ByteSizeUnit.BYTES).getBytes());
        assertEquals(BigInteger.valueOf(1), new ByteSize("0.1", ByteSizeUnit.BYTES).getBytes());
        assertEquals(BigInteger.valueOf(1500), new ByteSize("1.5", ByteSizeUnit.KILOBYTES).getBytes());
    }

    @Test
    public void theLargestUnitsStayExact(){
        // this is why getBytes() returns a BigInteger: a yobibyte does not fit in a long
        assertEquals(new BigInteger("1208925819614629174706176"),
                new ByteSize(1, ByteSizeUnit.YOBIBYTES).getBytes());
        try {
            new ByteSize(1, ByteSizeUnit.YOBIBYTES).getBytesAsLong();
            fail("ArithmeticException is expected");
        } catch (ArithmeticException e) {
            assertNotNull(e);
        }
    }

    /**
     * Every factor is a power of 1000 or of 1024, and both have no prime factor other than 2 and 5, so
     * every division by one of them terminates in base ten. The {@link ArithmeticException} that
     * {@link ByteSize#convertTo} documents for a non-terminating expansion cannot therefore be reached
     * through the units this type offers; the conversion is always exact.
     */
    @Test
    public void everyConversionBetweenUnitsIsExact(){
        for (ByteSizeUnit from : ByteSizeUnit.values())
            for (ByteSizeUnit to : ByteSizeUnit.values()) {
                ByteSize converted = new ByteSize(1, from).convertTo(to);
                assertEquals("converting 1 " + from + " to " + to + " and back changed the size",
                        new ByteSize(1, from).getBytes(), converted.getBytes());
            }
    }

    /**
     * A negative size is accepted as things stand. Written down because it is currently possible, not
     * because it is meaningful: whether it should be rejected is an open question.
     */
    @Test
    public void aNegativeSizeIsCurrentlyAccepted(){
        assertEquals(-5, new ByteSize(-5L, ByteSizeUnit.BYTES).getBytesAsLong());
        assertEquals(BigInteger.valueOf(-1), new ByteSize("-1.5", ByteSizeUnit.BYTES).getBytes());
    }

    // -------------------------------------------------------------------------------------------------
    // ordering
    // -------------------------------------------------------------------------------------------------

    @Test
    public void sizesAreOrderedByTheAmountOfDataRegardlessOfTheUnit(){
        // a mebibyte is 1048576 bytes, a megabyte is 1000000
        assertTrue(new ByteSize(1, ByteSizeUnit.MEGABYTES)
                .compareTo(new ByteSize(1, ByteSizeUnit.MEBIBYTES)) < 0);
        assertTrue(new ByteSize(1, ByteSizeUnit.MEBIBYTES)
                .compareTo(new ByteSize(1, ByteSizeUnit.MEGABYTES)) > 0);
        assertTrue(new ByteSize(1, ByteSizeUnit.KILOBYTES)
                .compareTo(new ByteSize(999L, ByteSizeUnit.BYTES)) > 0);
    }

    /**
     * Returning zero exactly when {@link ByteSize#equals} is true is what makes a sorted collection agree
     * with a hashed one on which sizes are duplicates.
     */
    @Test
    public void theOrderingIsConsistentWithEquals(){
        List<ByteSize> sizes = asList(
                new ByteSize(1, ByteSizeUnit.MEGABYTES),
                new ByteSize(1000000L, ByteSizeUnit.BYTES),
                new ByteSize("1000.0", ByteSizeUnit.KILOBYTES),
                new ByteSize(1, ByteSizeUnit.MEBIBYTES),
                new ByteSize(0L, ByteSizeUnit.BYTES),
                new ByteSize(-1L, ByteSizeUnit.BYTES));

        for (ByteSize a : sizes)
            for (ByteSize b : sizes)
                assertEquals("compareTo and equals disagree on " + a + " and " + b,
                        a.equals(b), a.compareTo(b) == 0);
    }

    @Test
    public void aTreeSetHoldsOneEntryPerSizeJustAsAHashSetDoes(){
        List<ByteSize> sizes = asList(
                new ByteSize(1, ByteSizeUnit.MEGABYTES),
                new ByteSize(1000000L, ByteSizeUnit.BYTES),
                new ByteSize("1000.0", ByteSizeUnit.KILOBYTES),
                new ByteSize(1, ByteSizeUnit.MEBIBYTES));

        assertEquals(new HashSet<>(sizes).size(), new TreeSet<>(sizes).size());
        assertEquals(2, new TreeSet<>(sizes).size());
    }

    @Test
    public void sizesCanBeSorted(){
        List<ByteSize> sizes = new ArrayList<>(asList(
                new ByteSize(1, ByteSizeUnit.GIGABYTES),
                new ByteSize(512L, ByteSizeUnit.BYTES),
                new ByteSize(1, ByteSizeUnit.MEBIBYTES),
                new ByteSize(1, ByteSizeUnit.MEGABYTES)));
        Collections.sort(sizes);

        assertEquals(asList(
                new ByteSize(512L, ByteSizeUnit.BYTES),
                new ByteSize(1, ByteSizeUnit.MEGABYTES),
                new ByteSize(1, ByteSizeUnit.MEBIBYTES),
                new ByteSize(1, ByteSizeUnit.GIGABYTES)), sizes);
    }

    @Test
    public void theOrderingIsAntisymmetricAndTransitive(){
        ByteSize small = new ByteSize(512L, ByteSizeUnit.BYTES);
        ByteSize medium = new ByteSize(1, ByteSizeUnit.KIBIBYTES);
        ByteSize large = new ByteSize(1, ByteSizeUnit.MEBIBYTES);

        assertEquals(Integer.signum(small.compareTo(medium)), -Integer.signum(medium.compareTo(small)));
        assertTrue(small.compareTo(medium) < 0 && medium.compareTo(large) < 0);
        assertTrue("transitivity", small.compareTo(large) < 0);
    }

    @Test
    public void comparingWithNullIsRejected(){
        try {
            new ByteSize(1L, ByteSizeUnit.BYTES).compareTo(null);
            fail("NullPointerException is expected");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }
}
