/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.dummy;

/**
 * Dummy class needed to allow the release on SonaType, whose validation fails on artifacts
 * without javadocs; please just ignore it.
 *
 * <p>Note: the whole <code>owner-java8-extras</code> artifact is deprecated and empty. The
 * Duration and ByteSize converters it used to provide moved into the <code>owner-extras</code>
 * artifact, on which this artifact now depends so that existing dependency declarations keep
 * resolving. It will be removed in a future release.</p>
 *
 * @author Matteo Baccan
 */
public class DummyJava8ExtrasReadme {
}
