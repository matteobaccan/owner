/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.aeonbits.owner.util.Util.blankLookingCharacterIn;
import static org.aeonbits.owner.util.Util.splitNumericAndChar;

/**
 * Reads the duration strings that OWNER accepts, in either of two formats:
 * <ul>
 *     <li>
 *         the ISO-8601 format that {@link java.time.Duration#parse(CharSequence)} supports, recognised by a
 *         leading <code>P</code> with an optional plus/minus prefix;
 *     </li>
 *     <li>
 *         a "<code>value time_unit</code>" string, where <code>value</code> is an integer and
 *         <code>time_unit</code> is one of <code>ns</code>, <code>us</code>, <code>ms</code>, <code>s</code>,
 *         <code>m</code>, <code>h</code>, <code>d</code> or one of their longer spellings. The unit is
 *         case sensitive, and milliseconds are assumed when it is absent.
 *     </li>
 * </ul>
 * <p>
 * This lives here, and not beside the converter that used to own it, so that the core can read a
 * {@link Duration} without depending on the <code>converters</code> package — which depends on the core in
 * turn, for the <code>Converter</code> interface it implements.
 * </p>
 *
 * @author Stefan Freyr Stefansson
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class DurationParser {

    /**
     * GREEK SMALL LETTER MU. In most fonts it cannot be told apart from the MICRO SIGN that the unit table
     * below holds, so someone whose keyboard or word processor produced this one sees a unit that looks
     * exactly right being refused. Held as a code point rather than as a character, both because the source
     * then survives any editor and because the two would otherwise be indistinguishable here too.
     */
    private static final int GREEK_SMALL_LETTER_MU = 0x03BC;

    // Suppresses default constructor, ensuring no one instantiate this class.
    private DurationParser() {}

    /**
     * Tells whether the given text is an ISO-8601 duration, and so is read by
     * {@link java.time.Duration#parse(CharSequence)} rather than by the <code>value time_unit</code> rules.
     *
     * @param text the text to examine; leading and trailing whitespace is not trimmed.
     * @return <code>true</code> if the text looks like an ISO-8601 duration.
     */
    public static boolean isIso8601(String text) {
        return text.startsWith("P") || text.startsWith("-P") || text.startsWith("+P");
    }

    /**
     * Reads a duration in either of the two supported formats.
     *
     * @param input the string to parse.
     * @return the duration it denotes.
     * @throws IllegalArgumentException if the input is invalid.
     * @throws java.time.format.DateTimeParseException if an ISO-8601 input is malformed.
     */
    public static Duration parse(String input) {
        // If it looks like a string that Duration.parse can handle, let's try that.
        if (isIso8601(input)) {
            return Duration.parse(input);
        }
        // ...otherwise we'll perform our own parsing
        return parseValueAndUnit(input);
    }

    /**
     * Parses a duration string. If no units are specified in the string, it is
     * assumed to be in milliseconds.
     *
     * This implementation was blatantly stolen/adapted from the typesafe-config project:
     * https://github.com/typesafehub/config/blob/v1.3.0/config/src/main/java/com/typesafe/config/impl/SimpleConfig.java#L551-L624
     *
     * @param input the string to parse
     * @return duration
     * @throws IllegalArgumentException if input is invalid
     */
    private static Duration parseValueAndUnit(String input) {
        String[] parts = splitNumericAndChar(input);
        String numberString = parts[0];
        String originalUnitString = parts[1];
        String unitString = originalUnitString;

        if (numberString.isEmpty()) {
            throw new IllegalArgumentException(String.format("No number in duration value '%s'", input));
        }

        if (unitString.length() > 2 && !unitString.endsWith("s")) {
            unitString = unitString + "s";
        }

        ChronoUnit units;
        // note that this is deliberately case-sensitive
        switch (unitString) {
            case "ns":
            case "nanos":
            case "nanoseconds":
                units = ChronoUnit.NANOS;
                break;
            case "us":
            case "\u00b5s":  // MICRO SIGN, written as an escape so no editor can mangle it
            case "micros":
            case "microseconds":
                units = ChronoUnit.MICROS;
                break;
            case "":
            case "ms":
            case "millis":
            case "milliseconds":
                units = ChronoUnit.MILLIS;
                break;
            case "s":
            case "seconds":
                units = ChronoUnit.SECONDS;
                break;
            case "m":
            case "minutes":
                units = ChronoUnit.MINUTES;
                break;
            case "h":
            case "hours":
                units = ChronoUnit.HOURS;
                break;
            case "d":
            case "days":
                units = ChronoUnit.DAYS;
                break;
            default:
                throw new IllegalArgumentException(unknownUnit(originalUnitString));
        }

        return Duration.of(number(input, numberString), units);
    }

    /**
     * The number is parsed here rather than inline so that the commonest way for it to be malformed — an
     * invisible character sitting between the digits and the unit — is reported as what it is. Left to
     * {@link Long#parseLong}, it raises a message quoting a value that looks perfectly correct.
     */
    private static long number(String input, String numberString) {
        try {
            return Long.parseLong(numberString);
        } catch (NumberFormatException e) {
            String blank = blankLookingCharacterIn(numberString);
            if (blank != null)
                throw new IllegalArgumentException(String.format(
                        "Could not read the number in duration value '%s': it contains %s, which reads as a "
                                + "space and is not one. It usually arrives by copying out of a word "
                                + "processor or a web page; replace it with an ordinary space.", input, blank), e);
            throw new IllegalArgumentException(
                    String.format("Could not read the number in duration value '%s'", input), e);
        }
    }

    /**
     * Says plainly when the unit was refused over a character that cannot be seen to be wrong. Guessing what
     * was meant and accepting it would be the other way out, but two code points that render alike are worth
     * keeping distinct in a configuration file, and the advice costs the reader one edit.
     */
    private static String unknownUnit(String unit) {
        if (unit.indexOf(GREEK_SMALL_LETTER_MU) >= 0)
            return String.format("Could not parse time unit '%s': it is written with GREEK SMALL LETTER MU "
                    + "(U+03BC), where the microsecond unit is spelled with MICRO SIGN (U+00B5). The two "
                    + "characters look the same and are not. Write 'us' instead, which is never ambiguous.",
                    unit);
        return String.format("Could not parse time unit '%s' (try ns, us, ms, s, m, h, d)", unit);
    }
}
