/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

/**
 * Thrown when a property marked as {@link Config.Mandatory} cannot be resolved: at creation time, listing all
 * the missing keys, or at access time when the property has become unavailable.
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public class MissingMandatoryPropertyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The keys that were missing. See {@link #getKeys()}. */
    private final List<String> keys;

    MissingMandatoryPropertyException(String key) {
        this(singletonList(key));
    }

    MissingMandatoryPropertyException(List<String> keys) {
        super(buildMessage(keys));
        // copied, not just wrapped: the caller keeps a reference to the list it handed over, and the keys
        // an exception reports have to stay the ones it was thrown for
        this.keys = unmodifiableList(new ArrayList<String>(keys));
    }

    /**
     * Lists the keys of the mandatory properties that could not be resolved.
     *
     * @return an unmodifiable list containing the missing keys.
     */
    public List<String> getKeys() {
        return keys;
    }

    private static String buildMessage(List<String> keys) {
        StringBuilder result = new StringBuilder(
                keys.size() == 1 ? "Missing mandatory property: " : "Missing mandatory properties: ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0)
                result.append(", ");
            result.append('\'').append(keys.get(i)).append('\'');
        }
        return result.toString();
    }
}
