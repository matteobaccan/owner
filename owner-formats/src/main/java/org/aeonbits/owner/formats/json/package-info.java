/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * Reads a configuration written in JSON - a source whose path ends in <code>.json</code> - flattening it
 * into the keys the rest of the library already speaks: <code>server.host</code>,
 * <code>servers[0].host</code>.
 * <p>
 * The parser is {@link org.aeonbits.owner.formats.json.JsonParser}, written here rather than taken from a
 * library, which is what lets <code>owner-formats</code> be added to a build without adding anything
 * else. It is small because the type is declared by the mapping interface and not by the file: a scalar
 * is kept as the text it was written with and converted where every other value is converted.
 * </p>
 * <p>
 * Three questions a JSON document can ask that a properties file cannot, answered the same way whenever
 * they come up: a <code>null</code> writes no key at all, so a
 * {@link org.aeonbits.owner.Config.DefaultValue} covers it; an empty array writes an empty value; and a
 * name repeated inside one object is refused rather than resolved, since the two readings of it - the
 * last one wins, or a list - are both somebody's expectation.
 * </p>
 */
package org.aeonbits.owner.formats.json;
