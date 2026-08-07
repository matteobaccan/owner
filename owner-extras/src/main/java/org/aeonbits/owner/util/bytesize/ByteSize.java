/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util.bytesize;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import static java.util.Objects.requireNonNull;

/**
 * A unit of byte size, such as "512 kilobytes".
 *
 * This class models a two part byte count size, one part being a value and the other part being a
 * {@link ByteSizeUnit}.
 *
 * This class supports converting to another {@link ByteSizeUnit}.
 *
 * <p>
 * Instances are immutable, and the class is <code>final</code>: {@link #equals(Object)} compares the number
 * of bytes across units, a statement that a subclass adding a field of its own could not honour.
 * </p>
 * <p>
 * Instances are ordered by the number of bytes they represent, consistently with {@link #equals(Object)}.
 * </p>
 * <p>
 * Instances are {@link Serializable}, and serialization preserves the unit the size was written in along
 * with the value, so what comes back reads the same way as what went in.
 * </p>
 *
 * @author Stefan Freyr Stefansson
 */
public final class ByteSize implements Comparable<ByteSize>, Serializable {

    private static final long serialVersionUID = 1L;

    private final BigDecimal value;
    private final ByteSizeUnit unit;

    /**
     * Creates a byte size value from two parts, a value and a {@link ByteSizeUnit}.
     *
     * @param value the value part of this byte size.
     * @param unit the unit part of this byte size.
     * @throws NullPointerException if either part is <code>null</code>.
     */
    public ByteSize(BigDecimal value, ByteSizeUnit unit){
        // rejected here rather than at the first arithmetic, so that the exception points at the line
        // where the missing part was written
        this.value = requireNonNull(value, "the value of a ByteSize cannot be null");
        this.unit = requireNonNull(unit, "the unit of a ByteSize cannot be null");
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
     * @throws NullPointerException if either part is <code>null</code>.
     */
    public ByteSize(String value, ByteSizeUnit unit){
        // checked before BigDecimal gets it, so the message is the same one on every JVM
        this(new BigDecimal(requireNonNull(value, "the value of a ByteSize cannot be null")), unit);
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

    /**
     * Compares two byte sizes by the amount of data they represent, so that <code>1 MiB</code> comes after
     * <code>1 MB</code> and the unit each of them is written in does not enter into it.
     * <p>
     * The ordering is <b>consistent with equals</b>: this method returns zero exactly when
     * {@link #equals(Object)} returns <code>true</code>. That is what makes a {@link java.util.TreeSet} of
     * byte sizes agree with a {@link java.util.HashSet} of the same sizes on which of them are duplicates.
     * </p>
     *
     * @param other the byte size to compare this one with.
     * @return a negative integer, zero, or a positive integer as this size is smaller than, equal to, or
     *         larger than the given one.
     * @throws NullPointerException if the given byte size is <code>null</code>.
     * @since 2.0.0
     */
    @Override
    public int compareTo(ByteSize other) {
        requireNonNull(other, "cannot compare a ByteSize with null");
        return getBytes().compareTo(other.getBytes());
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

    /**
     * Derived from the same number of bytes {@link #equals(Object)} compares, and from nothing else: two
     * sizes that are equal because they are the same amount written in different units, or with a different
     * number of decimals, have to agree on the hash code as well, or the type does not work as a key of a
     * {@link java.util.HashMap} nor as an element of a {@link java.util.HashSet}.
     */
    @Override
    public int hashCode() {
        return getBytes().hashCode();
    }

    /**
     * Deserialization builds an instance without running any constructor, so the check that rejects a
     * missing part has to be made again here: a stream is an input like any other, and one that does not
     * describe a byte size is refused rather than turned into an object that fails later.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (value == null)
            throw new InvalidObjectException("the value of a ByteSize cannot be null");
        if (unit == null)
            throw new InvalidObjectException("the unit of a ByteSize cannot be null");
    }
}
