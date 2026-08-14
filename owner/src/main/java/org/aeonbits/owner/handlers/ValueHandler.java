/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.handlers;

import java.io.Serializable;

/**
 * Turns the payload of a <code>${$name::payload}</code> marker into the value that replaces it.
 *
 * <h2>The marker</h2>
 * <p>
 * A value can name a handler instead of holding its own text:
 * </p>
 * <pre>
 *     db.password = ${$aes-gcm::c2FsdAAAAAAAAAAAaXYAAAAAAAAAY2lwaGVy}
 *     api.token   = ${$vault::secret/data/app:v2}
 *     tls.key     = ${$file::/run/secrets/tls.key}
 * </pre>
 * <p>
 * The library reads the envelope - the leading <code>$</code>, the name, the <code>::</code> - and hands
 * everything after the first <code>::</code> to the handler registered under that name, as text.
 * <b>The library owns the envelope and the handler owns the payload:</b> nothing between the
 * <code>::</code> and the closing brace is interpreted here, so a handler that wants parameters, versions
 * or paths defines them in whatever syntax suits it.
 * </p>
 * <p>
 * The only rule a payload must respect is that it cannot contain a <code>}</code>, which is what ends the
 * expression. Base64 never produces one; a path or a URL never does either.
 * </p>
 *
 * <h2>Being expansion is the point</h2>
 * <p>
 * A marker is resolved by the same substitution that resolves <code>${other.key}</code>, which is what
 * makes it reach everywhere a value can be read rather than only the methods of a
 * {@link org.aeonbits.owner.Config} interface:
 * </p>
 * <ul>
 *   <li>{@link org.aeonbits.owner.Accessible#fill(java.util.Map) fill} gets the resolved value, because
 *   <code>fill</code> expands;</li>
 *   <li>a value that refers to one - <code>jdbc.url=...${db.password}</code> - gets the resolved value
 *   too, because expansion recurses into what it finds;</li>
 *   <li>{@link org.aeonbits.owner.Accessible#store(java.io.OutputStream, String) store} writes the marker
 *   back, because the properties hold its text and not its answer.</li>
 * </ul>
 *
 * <h2>What a handler must not do</h2>
 * <p>
 * <b>Answer with the empty string when it cannot answer.</b> A marker names a value that has to come from
 * somewhere, so failing to produce it is a configuration error and must be thrown, not returned. This is
 * the same reasoning that makes an unregistered name an error rather than a fallback: for a password, the
 * empty string is the worst available answer, and it is indistinguishable from success.
 * </p>
 * <p>
 * <b>Expect its answer to be expanded.</b> It is not. What a handler returns replaces the marker verbatim,
 * <code>${</code> included - a decrypted secret that happens to contain one is a secret, not a template.
 * </p>
 *
 * <h2>Getting a handler into a factory</h2>
 * <p>
 * One way, deliberately:
 * </p>
 * <pre>
 *     ConfigFactory.registerValueHandler(new AesGcmHandler(passphrase));
 * </pre>
 * <p>
 * Unlike a {@link org.aeonbits.owner.loaders.Loader}, a handler is <b>not</b> discovered on the classpath.
 * A file format found on the classpath and enabled by itself reads files that are already yours; a handler
 * found on the classpath and enabled by itself would answer for values in them. Registration also settles
 * where a handler's own configuration comes from - a passphrase, a token, an endpoint - since the caller
 * constructs the instance and brings it.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public interface ValueHandler extends Serializable {

    /**
     * The name this handler answers to, which is what appears between <code>${$</code> and <code>::</code>.
     * <p>
     * It is the identifier of what the handler does, and the reason there is no registry of numbers here:
     * a second construction arrives as a second name. It is also where a key rotation goes -
     * <code>aes-gcm-2024</code> registered beside <code>aes-gcm-2025</code> turns a rotation into an edit
     * to the file, one value at a time.
     * </p>
     * <p>
     * A name may not be empty, and may not contain whitespace or any of <code>$ : { }</code>: those are
     * what tell a marker apart from an ordinary key, and a name using them would not be reachable.
     * </p>
     *
     * @return the name, never <code>null</code>.
     */
    String name();

    /**
     * Resolves the payload of a marker into the value that replaces it.
     *
     * @param payload everything after the first <code>::</code> and before the closing brace, as written,
     *                except that any <code>${...}</code> inside it has already been expanded.
     * @return the value, which replaces the whole marker and is <b>not</b> expanded further.
     * @throws IllegalArgumentException when the payload cannot be resolved. Throwing is the contract:
     *                                  returning <code>null</code> or the empty string reports a failure
     *                                  as a value.
     */
    String resolve(String payload);
}
