/*
 * Copyright (c) 2012-2016, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util.bytesize;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.aeonbits.owner.util.bytesize.ByteSizeStandard.*;

/**
 * Specifies the available byte size units that a {@link ByteSize} can have.
 *
 * A byte size unit has a {@link ByteSizeStandard} that dictates the base value to be raised to a given power as well
 * as the power value that dictates the magnitude of the unit. For example, a unit having the SI standard with a power
 * value of 4 is known as a terabyte while a unit having the IEC standard with a power value of 4 is known as a
 * tebibyte. The former has a factor of 1000^4 while the latter has a factor of 1024^4.
 *
 * @author Stefan Freyr Stefansson
 */
public enum ByteSizeUnit {
    /**
     * The base unit. Has a factor of 1.
     */
    BYTES("", "B", SI, 0),

    /**
     * The SI kilobyte. Has a factor of 1000^1.
     */
    KILOBYTES  ("kilo",  "KB",  SI, 1),
    /**
     * The IEC kibibyte. Has a factor of 1024^1.
     */
    KIBIBYTES  ("kibi",  "KiB", IEC, 1),
    /**
     * The SI megabyte. Has a factor of 1000^2.
     */
    MEGABYTES  ("mega",  "MB",  SI, 2),
    /**
     * The IEC mebibyte. Has a factor of 1024^2.
     */
    MEBIBYTES  ("mebi",  "MiB", IEC, 2),
    /**
     * The SI gigabyte. Has a factor of 1000^3.
     */
    GIGABYTES  ("giga",  "GB",  SI, 3),
    /**
     * The IEC gibibyte. Has a factor of 1024^3.
     */
    GIBIBYTES  ("gibi",  "GiB", IEC, 3),
    /**
     * The SI terabyte. Has a factor of 1000^4.
     */
    TERABYTES  ("tera",  "TB",  SI, 4),
    /**
     * The IEC tebibyte. Has a factor of 1024^4.
     */
    TEBIBYTES  ("tebi",  "TiB", IEC, 4),
    /**
     * The SI petabyte. Has a factor of 1000^5.
     */
    PETABYTES  ("peta",  "PB",  SI, 5),
    /**
     * The IEC pebibyte. Has a factor of 1024^5.
     */
    PEBIBYTES  ("pebi",  "PiB", IEC, 5),
    /**
     * The SI exabyte. Has a factor of 1000^6.
     */
    EXABYTES   ("exa",   "EB",  SI, 6),
    /**
     * The IEC exibyte. Has a factor of 1024^6.
     */
    EXBIBYTES  ("exbi",  "EiB", IEC, 6),
    /**
     * The SI zettabyte. Has a factor of 1000^7.
     */
    ZETTABYTES ("zetta", "ZB",  SI, 7),
    /**
     * The IEC zebibyte. Has a factor of 1024^7.
     */
    ZEBIBYTES  ("zebi",  "ZiB", IEC, 7),
    /**
     * The SI yottabyte. Has a factor of 1000^8.
     */
    YOTTABYTES ("yotta", "YB",  SI, 8),
    /**
     * The IEC yobibyte. Has a factor of 1024^8.
     */
    YOBIBYTES  ("yobi",  "YiB", IEC, 8);

    final String prefix;
    final String shortLabel;
    final ByteSizeStandard standard;
    final int power;

    ByteSizeUnit(String prefix, String shortLabel, ByteSizeStandard standard, int power) {
        this.prefix = prefix;
        this.shortLabel = shortLabel;
        this.standard = standard;
        this.power = power;
    }

    private static Map<String, ByteSizeUnit> makeUnitsMap() {
        Map<String, ByteSizeUnit> map = new HashMap<String, ByteSizeUnit>();
        for (ByteSizeUnit unit : ByteSizeUnit.values()) {
            map.put(unit.prefix + "byte", unit);
            map.put(unit.prefix + "bytes", unit);
            if (unit.prefix.length() == 0) {
                map.put("b", unit);
                map.put("", unit); // no unit specified means bytes
            } else {
                String first = unit.prefix.substring(0, 1);
                if (unit.standard == IEC) {
                    map.put(first, unit);        // 512m
                    map.put(first + "i", unit);  // 512mi
                    map.put(first + "ib", unit); // 512mib
                } else if (unit.standard == SI) {
                    map.put(first + "b", unit);  // 512kb
                } else {
                    throw new RuntimeException("broken MemoryUnit enum");
                }
            }
        }
        return map;
    }

    private static Map<String, ByteSizeUnit> unitsMap = makeUnitsMap();

    /**
     * Parses a string representation of a byte size unit and returns the corresponding {@link ByteSizeUnit}.
     *
     * There is support for various formats. Below is a list describing them where [prefix] represents the long form
     * prefix of the unit (such as "kilo", "mega", "tera", etc) and [first] represents the first letter in the prefix
     * (such as "k", "m", "t", etc):
     * <ul>
     *     <li>"" (empty string) - refers to the {@link ByteSizeUnit#BYTES}</li>
     *     <li>"b" - refers to the {@link ByteSizeUnit#BYTES}.</li>
     *     <li>"[prefix]byte" - the prefix determines what {@link ByteSizeStandard} is being used.</li>
     *     <li>"[prefix]bytes" - the prefix determines what {@link ByteSizeStandard} is being used.</li>
     *     <li>"[first]" - refers to the {@link ByteSizeStandard#IEC} standard for the corresponding prefix, eg. "k" is equal to "kibibyte"</li>
     *     <li>"[first]i" - refers to the {@link ByteSizeStandard#IEC} standard for the corresponding prefix, eg. "ki" is equal to "kibibyte"</li>
     *     <li>"[first]ib" - refers to the {@link ByteSizeStandard#IEC} standard for the corresponding prefix, eg. "kib" is equal to "kibibyte"</li>
     *     <li>"[first]b" - refers to the {@link ByteSizeStandard#SI} standard for the corresponding prefix, eg. "kb" is equal to "kilobyte"</li>
     * </ul>
     *
     * The parsing is case insensitive, meaning that the following strings are equivalent: "KiB", "kIb", "KIB", etc.
     *
     * This method will return <code>null</code> if the specified string is not a valid {@link ByteSizeUnit} string as
     * described above.
     *
     * @param unit the string representation of the desired {@link ByteSizeUnit}.
     *
     * @return  the {@link ByteSizeUnit} represented by the specified string or <code>null</code> if the string could
     *          not be translated into a known unit.
     */
    public static ByteSizeUnit parse(String unit) {
        return unitsMap.get(unit.toLowerCase());
    }

    /**
     * Returns whether this {@link ByteSizeUnit} is an SI unit.
     *
     * @return true iff this unit is an SI unit.
     */
    public boolean isSI(){
        return this.standard == ByteSizeStandard.SI;
    }

    /**
     * Returns whether this {@link ByteSizeUnit} is an IEC unit.
     *
     * @return true iff this unit is an IEC unit.
     */
    public boolean isIEC(){
        return this.standard == ByteSizeStandard.IEC;
    }

    /**
     * Gets the multiplication factor for this {@link ByteSizeUnit}.
     *
     * Returns the result of raising the poweOf value of this units standard to the power specified in this unit.
     *
     * @return the factor by which to multiply for this unit.
     */
    public BigDecimal getFactor(){
        return BigDecimal.valueOf(standard.powerOf).pow(power);
    }

    /**
     * Returns the long string representation of this unit, such as "kilobytes", "megabytes" or "bytes".
     *
     * Note that this method always returns the plural form.
     *
     * @return the plural long string representation of this unit.
     */
    public String toStringLongForm(){
        return prefix + "bytes";
    }

    /**
     * Returns the short string representation of this unit, such as "KiB", "B" or "MB".
     *
     * @return the short string representation of this unit.
     */
    public String toStringShortForm(){
        return shortLabel;
    }
}
