/*
 * Copyright (c) 2012-2016, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util.bytesize;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A unit of byte size, such as "512 kilobytes".
 *
 * This class models a two part byte count size, one part being a value and the other part being a
 * {@link ByteSizeUnit}.
 *
 * This class supports converting to another {@link ByteSizeUnit}.
 *
 * @author Stefan Freyr Stefansson
 */
public class ByteSize {
    private final BigDecimal value;
    private final ByteSizeUnit unit;

    /**
     * Creates a byte size value from two parts, a value and a {@link ByteSizeUnit}.
     *
     * @param value the value part of this byte size.
     * @param unit the unit part of this byte size.
     */
    public ByteSize(BigDecimal value, ByteSizeUnit unit){
        this.value = value;
        this.unit = unit;
    }

    /**
     * Creates a byte size value from a <code>long</code> value representing the number of bytes.
     *
     * The unit part of this byte size will be {@link ByteSizeUnit#BYTES}.
     *
     * @param bytes the number of bytes this {@link ByteSize} instance should represent
     */
    public ByteSize(long bytes){
        this(bytes, ByteSizeUnit.BYTES);
    }

    /**
     * Creates a byte size value from a <code>String</code> value and a {@link ByteSizeUnit}.
     *
     * @param value the value part of this byte size
     * @param unit the unit part of this byte size
     */
    public ByteSize(String value, ByteSizeUnit unit){
        this(new BigDecimal(value), unit);
    }

    /**
     * Creates a byte size value from a <code>long</code> value and a {@link ByteSizeUnit}.
     *
     * @param value the value part of this byte size
     * @param unit the unit part of this byte size
     */
    public ByteSize(long value, ByteSizeUnit unit){
        this(BigDecimal.valueOf(value), unit);
    }

    /**
     * Creates a byte size value from a <code>double</code> value and a {@link ByteSizeUnit}.
     *
     * @param value the value part of this byte size
     * @param unit the unit part of this byte size
     */
    public ByteSize(double value, ByteSizeUnit unit){
        this(BigDecimal.valueOf(value), unit);
    }

    /**
     * Returns the number of bytes that this byte size represents after multiplying the unit factor with the value.
     *
     * Since the value part can be a represented by a decimal, there is some possibility of a rounding error. Therefore,
     * the result of multiplying the value and the unit factor are always rounded towards positive infinity to the
     * nearest integer value (see {@link RoundingMode#CEILING}) to make sure that this method never gives values that
     * are too small.
     *
     * @return number of bytes this byte size represents after factoring in the unit.
     */
    public BigInteger getBytes(){
        return value.multiply(unit.getFactor()).setScale(0, RoundingMode.CEILING).toBigIntegerExact();
    }

    /**
     * Returns the number of bytes that this byte size represents as a <code>long</code> after multiplying the unit
     * factor with the value, throwing an exception if the result overflows a <code>long</code>.
     *
     * @throws ArithmeticException if the result overflows a <code>long</code>
     *
     * @return the number of bytes that this byte size represents after factoring in the unit.
     */
    public long getBytesAsLong(){
        return getBytes().longValueExact();
    }

    /**
     * Returns the number of bytes that this byte size represents as an <code>int</code> after multiplying the unit
     * factor with the value, throwing an exception if the result overflows an <code>int</code>.
     *
     * @throws ArithmeticException if the result overflows an <code>int</code>
     *
     * @return the number of bytes that this byte size represents after factoring in the unit.
     */
    public int getBytesAsInt() {
        return getBytes().intValueExact();
    }

    /**
     * Creates a new {@link ByteSize} representing the same byte size but in a different unit.
     *
     * Scale of the value (number of decimal points) is handled automatically but if a non-terminating decimal expansion
     * occurs, an {@link ArithmeticException} is thrown.
     *
     * @param unit the unit for the new {@link ByteSize}.
     *
     * @throws ArithmeticException if a non-terminating decimal expansion occurs during calculation.
     *
     * @return a new {@link ByteSize} instance representing the same byte size as this but using the specified unit.
     */
    public ByteSize convertTo(ByteSizeUnit unit){
        BigDecimal bytes = this.value.multiply(this.unit.getFactor()).setScale(0, RoundingMode.CEILING);
        return new ByteSize(bytes.divide(unit.getFactor()), unit);
    }

    @Override
    public String toString() {
        return value.toString() + " " + unit.toStringShortForm();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ByteSize byteSize = (ByteSize) o;

        return getBytes().equals(byteSize.getBytes());
    }

    @Override
    public int hashCode() {
        int result = value.hashCode();
        result = 31 * result + unit.hashCode();
        return result;
    }
}
