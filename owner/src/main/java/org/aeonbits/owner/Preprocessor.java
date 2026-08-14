/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

/**
 * Preprocessor interface specifies how to pre-process an input string coming from a property value before being used by
 * OWNER.
 *
 * @author Luigi R. Viggiano
 * @since 1.0.9
 */
@FunctionalInterface
public interface Preprocessor {
    /**
     * Pre-processes the given input string before it is used by the library.
     *
     * @param input the raw property value.
     * @return the processed property value.
     */
    String process(String input);

    /**
     * Called instead of {@link #process(String)} when the property is <b>absent</b> — no value in any
     * source and no {@link Config.DefaultValue} — so that a preprocessor can supply one, or refuse.
     * <p>
     * It answers <code>null</code> by default, which leaves the property absent and is exactly what
     * every preprocessor written before 2.0.0 already does. That is deliberate:
     * {@link #process(String)} has never been handed a <code>null</code>, so anything already written is
     * free to go on calling methods on its input.
     * </p>
     * <p>
     * <b>The key is passed because without it there would be nothing useful to do here.</b> A
     * preprocessor that only learned of the absence could answer with a constant, and a constant is what
     * {@link Config.DefaultValue} already is. With the key it can look the value up somewhere the
     * library knows nothing about, or name the property in what it throws:
     * </p>
     * <pre>
     *     public String processAbsent(String key) {
     *         return vault.lookup(key);   // null if the vault has not got it either
     *     }
     * </pre>
     * <p>
     * <b>To make a property required, use {@link Config.Mandatory} instead</b>: it throws
     * {@link MissingMandatoryPropertyException} naming the key, and needs no code at all. This is for
     * the case that one does not cover — computing a value rather than insisting on one.
     * </p>
     * <p>
     * What is returned here goes on through the rest of the chain: preprocessors after this one see it
     * through {@link #process(String)}, and variable expansion, decryption, argument formatting and
     * conversion all follow as they would for a value read from a file. If it is still <code>null</code>
     * when the chain ends the property is absent — and a {@link Config.Mandatory} one throws then, so a
     * preprocessor that supplies a value <b>satisfies</b> the requirement rather than racing it.
     * </p>
     * <p>
     * <b>That holds at startup too, and it costs a second call.</b> {@link Config.Mandatory} is checked
     * when the configuration is created, before any method is invoked, so this is asked once there —
     * only for a property the sources are silent about — and again when the property is first read. A
     * preprocessor that reaches out to something expensive should expect both. One that merely declines
     * an absence is never called at all when the sources have answered.
     * </p>
     *
     * @param key the key that was looked up and not found, prefix and variables already resolved.
     * @return the value to use, or <code>null</code> to leave the property absent.
     * @since 2.0.0
     */
    default String processAbsent(String key) {
        return null;
    }
}
