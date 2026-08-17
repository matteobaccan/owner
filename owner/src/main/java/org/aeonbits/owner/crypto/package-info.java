/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
/**
 * The seam through which a decryptor of yours is called: what
 * {@link org.aeonbits.owner.Config.EncryptedValue} and
 * {@link org.aeonbits.owner.Config.DecryptorClass} name, and the base classes that save an implementation
 * from writing the same two methods.
 * <p>
 * <b>You supply the cryptography; nothing here ships any.</b> The one implementation in the package,
 * {@link org.aeonbits.owner.crypto.IdentityDecryptor}, returns the value it was given and exists so that
 * "no decryptor" is an object rather than a null.
 * </p>
 * <p>
 * Since 2.0.0 this is the older of two ways to keep a secret in a configuration, and the other one is
 * usually the one to write: a value that names what resolves it -
 * <code>${$aes-gcm::&hellip;}</code> - is read by a {@link org.aeonbits.owner.handlers.ValueHandler}, of
 * which the library <b>does</b> ship two, and it is understood by every path that reads a value rather
 * than only by the annotated method. This one stays because configurations depend on it, and because a
 * decryptor written against an HSM or a key manager is a decryptor nobody else can write for you.
 * </p>
 */
package org.aeonbits.owner.crypto;
