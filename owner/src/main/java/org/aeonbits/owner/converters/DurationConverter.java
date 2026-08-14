/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.converters;

import org.aeonbits.owner.Converter;
import org.aeonbits.owner.util.DurationParser;

import java.lang.reflect.Method;
import java.time.Duration;

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
 * <p>
 * The reading itself is done by {@link DurationParser}, which the core uses directly to convert a
 * {@link Duration} without being asked to. This class is the {@link Converter} that names it, for the
 * methods that ask for it by hand with <code>@ConverterClass</code>.
 * </p>
 */
public class DurationConverter implements Converter<Duration> {
    /**
     * Built with no arguments: a converter named by {@link org.aeonbits.owner.Config.ConverterClass}
     * is instantiated reflectively, so this constructor is part of the contract rather than an
     * accident of there being no state.
     */
    public DurationConverter() {
    }


    @Override
    public Duration convert(Method method, String input) {
        return DurationParser.parse(input);
    }
}
