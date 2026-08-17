/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * Reads a configuration written in YAML - a source whose path ends in <code>.yaml</code> or
 * <code>.yml</code> - flattening it into the keys the rest of the library already speaks.
 * <p>
 * <b>A documented subset, not a YAML implementation.</b> Block mappings and sequences, quoted and plain
 * scalars, block scalars with their chomping, flow collections and comments are read; anchors, aliases
 * and merge keys, tags, complex keys, a second document in the same file, and a value continued onto the
 * next line without <code>|</code> or <code>&gt;</code> are <b>refused by name and by line</b>. A parser
 * that half-understood any of them would change the meaning of somebody's file instead of declining it.
 * </p>
 * <p>
 * That is also why {@link org.aeonbits.owner.formats.yaml.YamlParser} is around seven hundred lines
 * rather than several thousand: the mapping interface declares the types, so the implicit typing that
 * turns <code>no</code> into <code>false</code> - the Norway problem - is work that never arises here. A
 * scalar is handed back as the text it was written with.
 * </p>
 */
package org.aeonbits.owner.formats.yaml;
