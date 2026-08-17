/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * Reads a configuration written in <a href="https://toml.io/en/v1.0.0">TOML v1.0.0</a> - a source whose
 * path ends in <code>.toml</code> - flattening it into the keys the rest of the library already speaks.
 * <p>
 * Of the formats here this is the one that needed the least persuading: the flattening convention was
 * already shaped like TOML. A dotted key <i>is</i> the flattening, <code>[table]</code> is a prefix, and
 * <code>[[servers]]</code> is <code>servers[0]</code>, which is what a
 * {@link java.util.List} of nested configuration objects reads.
 * </p>
 * <p>
 * <b>Values are handed back as written, except where TOML spells one value several ways.</b>
 * <code>1_000</code>, <code>0xDEAD</code>, <code>0o755</code> and <code>0b1101</code> are all one integer
 * and none of them would convert, so they become plain decimal; <code>inf</code> and <code>nan</code>
 * become what {@link java.lang.Double#parseDouble} answers to; and the space TOML allows in place of the
 * <code>T</code> of a date-time becomes a <code>T</code>, which is what
 * {@link java.time.LocalDateTime#parse} wants. Strings are untouched.
 * </p>
 */
package org.aeonbits.owner.formats.toml;
