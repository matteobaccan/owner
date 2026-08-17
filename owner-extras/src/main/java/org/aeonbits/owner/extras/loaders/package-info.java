/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * The loaders that cannot be written without somebody else's library on the class path: HOCON, which
 * reads an <code>application.conf</code> through Typesafe Config, and ZooKeeper, which reads a node
 * through Curator.
 * <p>
 * <b>That is the whole rule for what lives here rather than in the core or in
 * <code>owner-formats</code>.</b> A format OWNER parses itself ships with the library, because a
 * dependency is a thing every user of it inherits; a format defined by an implementation - HOCON is
 * whatever Typesafe Config accepts, and reimplementing its substitutions would mean reading somebody's
 * file differently from the tool that wrote it - is read by that implementation or not at all.
 * </p>
 * <p>
 * Each loader comes in two classes for the same reason
 * {@link org.aeonbits.owner.extras.validation.JakartaBeanValidation} does: a
 * {@link org.aeonbits.owner.loaders.Loader} is discovered and instantiated before anybody asks for it,
 * so the class that names the optional library is a second one, untouched until there is a source of
 * that kind to read.
 * </p>
 */
package org.aeonbits.owner.extras.loaders;
