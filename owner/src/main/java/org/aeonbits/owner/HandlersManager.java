/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.handlers.ValueHandler;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.aeonbits.owner.util.Util.unsupported;

/**
 * Holds the {@link ValueHandler}s a factory knows, by name, and reads the envelope of a
 * <code>${$name::payload}</code> marker.
 * <p>
 * The counterpart of {@link LoadersManager}, with one deliberate difference: <b>nothing is discovered</b>.
 * A loader found on the classpath reads files that are already yours; a handler found on the classpath
 * would answer for the values inside them, and a jar arriving as a transitive dependency is not a decision
 * anybody took. See {@link ValueHandler} for the rest of that reasoning.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
class HandlersManager implements Serializable {

    private static final long serialVersionUID = 4472519934091827155L;

    /** What opens a marker, after the <code>${</code> the substitution has already read. */
    private static final char MARKER_START = '$';

    /** What separates the name from the payload. */
    static final String NAME_SEPARATOR = "::";

    /** The characters a name may not contain, being the ones that delimit a marker or a variable. */
    private static final String FORBIDDEN_IN_NAME = "$:{}";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** In registration order, so that the message naming what is registered reads as the caller wrote it. */
    private final Map<String, ValueHandler> handlers = new LinkedHashMap<>();

    /**
     * Tells whether an expression is a handler reference rather than a key.
     * <p>
     * It is one when it begins with <code>$</code> and contains <code>::</code>, and that is decided
     * <b>before</b> the expression is looked up as a property key. The alternative - fall back on a
     * handler only when no key of that name exists - would mean a marker naming a handler nobody
     * registered quietly becomes the empty string, which for a password is the worst answer available.
     * Nothing is lost by deciding first: a property key beginning with <code>$</code> and containing
     * <code>::</code> is not a key anybody writes, and one that did would have been unreadable through
     * {@code ${...}} anyway.
     * </p>
     */
    static boolean isMarker(String expression) {
        return !expression.isEmpty()
                && expression.charAt(0) == MARKER_START
                && expression.indexOf(NAME_SEPARATOR) > 1;
    }

    /** The name of the handler an expression refers to; only meaningful when {@link #isMarker} said so. */
    static String nameOf(String expression) {
        return expression.substring(1, expression.indexOf(NAME_SEPARATOR));
    }

    /**
     * Resolves a marker through the handler it names.
     *
     * @param expression the text between <code>${</code> and <code>}</code>, for which {@link #isMarker}
     *                   answered <code>true</code>.
     * @return whatever the handler answered, which replaces the marker and is not expanded further.
     * @throws UnsupportedOperationException if no handler goes by that name.
     */
    String resolve(String expression) {
        String name = nameOf(expression);
        String payload = expression.substring(name.length() + 1 + NAME_SEPARATOR.length());
        ValueHandler handler = handlerNamed(name);
        if (handler == null)
            // the payload is deliberately not in the message: it is the one part of a marker that may be
            // cipher text, a secret path or a token, and an exception travels into logs
            throw unsupported("No value handler is registered under the name '%s', which a value refers to "
                            + "as ${$%s::...}. A name that is not registered is an error and not a "
                            + "variable: left to resolve, it would answer with the empty string, and the "
                            + "values written this way are the ones where that is worst. Register it with "
                            + "ConfigFactory.registerValueHandler(...) before creating the configuration. "
                            + "Registered %s.",
                    name, name, registered());
        return handler.resolve(payload);
    }

    private ValueHandler handlerNamed(String name) {
        lock.readLock().lock();
        try {
            return handlers.get(name);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** What is registered, for a message that has to be actionable to be worth printing. */
    private String registered() {
        lock.readLock().lock();
        try {
            if (handlers.isEmpty())
                return "so far: nothing";
            return "so far: " + String.join(", ", handlers.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Registers a handler under the name it answers to, replacing one registered under the same name.
     * <p>
     * Replacing is what makes a handler configurable at all - the same name registered again with a
     * different passphrase is a key rotation - and it is the reason this is a map rather than the list
     * {@link LoadersManager} keeps: a loader is chosen by asking each one whether it accepts a source,
     * while a handler is chosen by a name that either matches or does not.
     * </p>
     *
     * @throws IllegalArgumentException if the handler is <code>null</code>, or its name is null, empty, or
     *                                  contains a character that would make it unreachable.
     */
    final void registerValueHandler(ValueHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException("value handler can't be null");
        String name = handler.name();
        checkName(name, handler);
        lock.writeLock().lock();
        try {
            handlers.put(name, handler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Refuses at registration a name that could not be written in a marker, which is the one moment the
     * author of the handler is present to be told. Discovered later, the same mistake is a marker that
     * refuses to resolve and no obvious reason why.
     */
    private static void checkName(String name, ValueHandler handler) {
        String who = handler.getClass().getName();
        if (name == null)
            throw new IllegalArgumentException(who + " has a null name");
        if (name.isEmpty())
            throw new IllegalArgumentException(who + " has an empty name");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c) || FORBIDDEN_IN_NAME.indexOf(c) != -1)
                throw new IllegalArgumentException(String.format(
                        "%s is named '%s', which contains '%s'. A handler is named in a value as "
                                + "${$%s::payload}, so a name may not be empty and may not contain "
                                + "whitespace or any of %s - those are what tell a marker apart from an "
                                + "ordinary key.",
                        who, name, Character.isWhitespace(c) ? "whitespace" : String.valueOf(c), name,
                        FORBIDDEN_IN_NAME));
        }
    }

    void clear() {
        lock.writeLock().lock();
        try {
            handlers.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

}
