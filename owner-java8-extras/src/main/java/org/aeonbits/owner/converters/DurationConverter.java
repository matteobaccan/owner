/*
 * Copyright (c) 2012-2016, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.converters;

import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * A duration converter for the OWNER configuration system.
 *
 * This converter will convert various duration formatted strings over to {@link java.time.Duration} objects.
 *
 * The class supports two formats for the duration string:
 * <ul>
 *     <li>
 *         The ISO-8601 based format that the {@link java.time.Duration#parse(CharSequence)} method supports
 *         (<a href="https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-"
 *         target="_blank">see the official Java 8 documentation</a>, although note that currently there is an
 *         <a href="https://bugs.openjdk.java.net/browse/JDK-8146173" target="_blank">error in the documentation</a>).
 *         The implementation will check whether the input string starts with <code>P</code> with an optional plus/minus
 *         prefix and if so, will use this method for parsing.
 *     </li>
 *     <li>
 *         A "<code>value time_unit</code>" string where the <code>value</code> is an integer and <code>time_unit</code>
 *         is one of:
 *         <ul>
 *             <li>ns / nanos / nanoseconds</li>
 *             <li>us / µs / micros / microseconds</li>
 *             <li>ms / millis / milliseconds</li>
 *             <li>s / seconds</li>
 *             <li>m / minutes</li>
 *             <li>h / hours</li>
 *             <li>d / days</li>
 *         </ul>
 *         <p>
 *         Note that the <code>time_unit</code> string is case sensitive.
 *         <p>
 *         If no <code>time_unit</code> is specified, <code>milliseconds</code> is assumed.
 *     </li>
 * </ul>
 */
public class DurationConverter implements Converter<Duration> {

    @Override
    public Duration convert(Method method, String input) {
        // If it looks like a string that Duration.parse can handle, let's try that.
        if (input.startsWith("P") || input.startsWith("-P") || input.startsWith("+P")) {
            return Duration.parse(input);
        }
        // ...otherwise we'll perform our own parsing
        return parseDuration(input);
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
    private static Duration parseDuration(String input) {
        String[] parts = ConverterUtil.splitNumericAndChar(input);
        String numberString = parts[0];
        String originalUnitString = parts[1];
        String unitString = originalUnitString;

        if (numberString.length() == 0) {
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
            case "µs":
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
                throw new IllegalArgumentException(
                        String.format("Could not parse time unit '%s' (try ns, us, ms, s, m, h, d)", originalUnitString));
        }

        return Duration.of(Long.parseLong(numberString), units);
    }
}
