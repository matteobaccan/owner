/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * The converters this library ships for types its own conversion rules do not already cover: a
 * {@link java.time.Duration} and a {@link org.aeonbits.owner.util.bytesize.ByteSize}.
 * <p>
 * The two are not offered the same way, and the difference is the rule. <code>Duration</code> belongs to
 * the JDK, so it converts <b>automatically</b> - a method returning one needs no annotation. `ByteSize` is
 * ours, so its converter has to be <b>named</b> with {@link org.aeonbits.owner.Config.ConverterClass}: a
 * type of ours cannot start changing what a value means in a configuration that predates it.
 * </p>
 * <p>
 * Both are ordinary {@link org.aeonbits.owner.Converter} implementations, written no differently from one
 * of yours, and both are here rather than in the core because the core's conversion is a closed set of
 * rules while these are two decisions about two types.
 * </p>
 */
package org.aeonbits.owner.converters;
