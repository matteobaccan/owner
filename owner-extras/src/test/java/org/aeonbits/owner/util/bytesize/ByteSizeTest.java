/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util.bytesize;

import org.junit.Test;
import java.math.BigInteger;
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
}
