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

    /** The size as written, in {@link #unit}: the exact number, before any rounding to whole bytes. */
    private final BigDecimal value;
    /** The unit {@link #value} is expressed in, which is also the one {@link #toString()} writes back. */
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
        return exactBytes().setScale(0, RoundingMode.CEILING).toBigIntegerExact();
    }

    /**
     * The number of bytes this size represents, with nothing rounded away: the value multiplied by the
     * factor of its unit, and no more.
     * <p>
     * This is what identity is built on — {@link #equals(Object)}, {@link #hashCode()} and
     * {@link #compareTo(ByteSize)} — while {@link #getBytes()} keeps rounding towards positive infinity,
     * which is what a caller allocating a buffer needs. Deciding identity on the rounded count made
     * <code>0.4 B</code> equal to <code>0.6 B</code>, and a {@link java.util.HashSet} of the two hold one
     * element; a fraction of a byte is an odd thing to write, but two different numbers are two different
     * numbers, and a rounding meant to keep a buffer large enough has no business merging them.
     * </p>
     */
    private BigDecimal exactBytes() {
        return value.multiply(unit.getFactor());
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
     * <p>
     * The conversion changes nothing but the unit: what comes back {@link #equals(Object)} what went in, in
     * every unit and for every value. It used to round the byte count towards positive infinity first, the
     * way {@link #getBytes()} does, which turned <code>1.5 B</code> into <code>2 B</code> — a conversion
     * that reports a size other than the one it was given, and it did so even when the unit asked for was
     * the one the size was already written in. The rounding belongs to {@link #getBytes()}, whose caller
     * wants a whole number of bytes; it does not belong here.
     * </p>
     *
     * @param unit the unit for the new {@link ByteSize}.
     *
     * @throws ArithmeticException if a non-terminating decimal expansion occurs during calculation.
     *
     * @return a new {@link ByteSize} instance representing the same byte size as this but using the specified unit.
     */
    public ByteSize convertTo(ByteSizeUnit unit){
        return new ByteSize(exactBytes().divide(unit.getFactor()), unit);
    }

    /**
     * Returns the same byte size expressed in the unit of the given standard that makes it readable: the
     * largest one in which the value does not fall below one, so that 2048576 bytes read as
     * <code>2.048576 MB</code> in {@link ByteSizeStandard#SI} and as <code>1.95367431640625 MiB</code> in
     * {@link ByteSizeStandard#IEC}.
     * <p>
     * Where {@link #convertTo(ByteSizeUnit)} needs to be told the unit, this method needs only the family
     * of units to pick from, which is what one usually has: a size read from a configuration file is to be
     * logged or shown, and the unit that suits it depends on how large it turned out to be.
     * </p>
     * <p>
     * The result is <b>canonical</b>: two sizes that are equal give the same answer, whatever unit each of
     * them was written in, so <code>1 MB</code> and <code>1000000 B</code> both read as <code>1 MB</code>
     * in SI. That is what makes this the natural way to display the outcome of a calculation, where the
     * unit of the operands is an accident of how they were written.
     * </p>
     * <p>
     * The conversion is exact: every factor is a power of 1000 or of 1024, neither of which has a prime
     * factor beyond 2 and 5, so no division involved here can fail to terminate. A size below one byte, or
     * zero, reads in bytes, there being no larger unit that fits; a negative size keeps its sign and takes
     * the unit its magnitude asks for.
     * </p>
     *
     * @param standard the family of units to express this size in.
     * @return an equal byte size, written in the unit of that standard that suits it.
     * @throws NullPointerException if the standard is <code>null</code>.
     * @since 2.0.0
     */
    public ByteSize in(ByteSizeStandard standard) {
        requireNonNull(standard, "the standard to express a ByteSize in cannot be null");
        ByteSize converted = convertTo(unitFor(getBytes().abs(), standard));
        return new ByteSize(canonicalScale(converted.value), converted.unit);
    }

    /**
     * The same number written the one way this method answers in. The scale of a division is inherited
     * from its operands — <code>1000.0 KB</code> gives <code>1.0 MB</code> where <code>1000 KB</code>
     * gives <code>1 MB</code> — and {@link #in(ByteSizeStandard)} promises that two equal sizes read
     * alike, so the trailing zeros that record nothing but how the operand was written are dropped. It
     * changes the spelling and not the size: {@link #equals(Object)} and {@link #compareTo(ByteSize)}
     * ignore the scale.
     * <p>
     * Stripping alone would go one step too far, since it is free to answer with a negative scale and
     * <code>1000</code> would then read <code>1E+3</code> — which happens for a size past the largest
     * unit there is. The scale is brought back to zero in that case, which is where a whole number
     * belongs.
     * </p>
     */
    private static BigDecimal canonicalScale(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * The largest unit of the given standard whose factor does not exceed the given number of bytes.
     * {@link ByteSizeUnit#BYTES} is the starting point rather than a candidate, since it is the answer
     * whenever no larger unit fits, and it belongs to both families however it is labelled.
     */
    private static ByteSizeUnit unitFor(BigInteger bytes, ByteSizeStandard standard) {
        ByteSizeUnit result = ByteSizeUnit.BYTES;
        for (ByteSizeUnit candidate : ByteSizeUnit.values()) {
            if (candidate == ByteSizeUnit.BYTES || !belongsTo(candidate, standard))
                continue;
            if (candidate.getFactor().toBigInteger().compareTo(bytes) <= 0)
                result = candidate;
        }
        return result;
    }

    private static boolean belongsTo(ByteSizeUnit unit, ByteSizeStandard standard) {
        return standard == ByteSizeStandard.SI ? unit.isSI() : unit.isIEC();
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
        return exactBytes().compareTo(other.exactBytes());
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

        // compareTo rather than equals: the scale is how the size was written, not part of what it is, so
        // 1 KB and 1.0 KB are the same size and BigDecimal.equals would say otherwise
        return exactBytes().compareTo(byteSize.exactBytes()) == 0;
    }

    /**
     * Derived from the same number of bytes {@link #equals(Object)} compares, and from nothing else: two
     * sizes that are equal because they are the same amount written in different units, or with a different
     * number of decimals, have to agree on the hash code as well, or the type does not work as a key of a
     * {@link java.util.HashMap} nor as an element of a {@link java.util.HashSet}.
     * <p>
     * The trailing zeros are stripped first because that is exactly what {@link BigDecimal#equals(Object)}
     * counts and {@link BigDecimal#compareTo(BigDecimal)} does not: <code>1000</code> and
     * <code>1.000E+3</code> are the same size written twice, and they have to hash alike.
     * </p>
     */
    @Override
    public int hashCode() {
        return exactBytes().stripTrailingZeros().hashCode();
    }

    /**
     * Deserialization builds an instance without running any constructor, so the check that rejects a
     * missing part has to be made again here: a stream is an input like any other, and one that does not
     * describe a byte size is refused rather than turned into an object that fails later.
     *
     * @param in the stream being read.
     * @throws IOException if the stream cannot be read, or does not describe a byte size.
     * @throws ClassNotFoundException if a class named by the stream cannot be found.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (value == null)
            throw new InvalidObjectException("the value of a ByteSize cannot be null");
        if (unit == null)
            throw new InvalidObjectException("the unit of a ByteSize cannot be null");
    }
}
