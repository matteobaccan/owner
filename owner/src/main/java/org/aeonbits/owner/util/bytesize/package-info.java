/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * A size in bytes as a type rather than as a number: <code>10 MB</code> read as what it says, in both the
 * standards that phrase means.
 * <p>
 * A configuration that declares <code>long maxUpload()</code> and multiplies by 1024 somewhere else has
 * put the unit in the code and left the file saying nothing. {@link org.aeonbits.owner.util.bytesize.ByteSize}
 * keeps the unit where it was written, and {@link org.aeonbits.owner.util.bytesize.ByteSizeStandard} is
 * the reason it can: <code>1 KB</code> is a thousand bytes and <code>1 KiB</code> is 1024, which is the
 * one thing everybody who has ever sized a buffer has got wrong at least once.
 * </p>
 * <p>
 * A method returning one asks for it with
 * <code>&#64;ConverterClass(ByteSizeConverter.class)</code>: this is the library's own type, not the
 * JDK's, so it does not convert automatically the way {@link java.time.Duration} does.
 * </p>
 */
package org.aeonbits.owner.util.bytesize;
