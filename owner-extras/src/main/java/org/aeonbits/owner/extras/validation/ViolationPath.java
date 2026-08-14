/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.extras.validation;

/**
 * Turns a Bean Validation property path into the one thing a reader of a configuration still needs from it:
 * <b>which element</b> of a list or of a group failed.
 *
 * <p>
 * A violation is reported here against the property key, which is the line to go and change; the path the
 * provider builds says the same thing in its own vocabulary - <code>port.&lt;return value&gt;</code> - and
 * adds nothing. It does add something when the value is a container:
 * <code>servers.&lt;return value&gt;[2].&lt;list element&gt;</code> means the third element, and without it
 * a reader is told that one of ten servers is wrong and not which.
 * </p>
 *
 * <p>
 * It works on the path as text, and takes no validation API with it, which is what lets both the
 * <code>javax</code> and the <code>jakarta</code> side of this package share it. The node names differ
 * between providers and versions - <code>&lt;list element&gt;</code>, <code>&lt;iterable element&gt;</code>,
 * <code>&lt;map value&gt;</code> - so none of them is matched: only the indices in brackets, which every
 * provider spells the same way because the specification does.
 * </p>
 *
 * @author Matteo Baccan
 */
final class ViolationPath {

    /** Don't let anyone instantiate this class */
    private ViolationPath() {
    }

    /**
     * Composes the message of a violation, naming the failing element when there is one.
     *
     * @param path       the property path as the provider spelled it.
     * @param methodName the name of the method that was checked.
     * @param message    the message the provider produced.
     * @return the message, prefixed by the element that failed when the value was a container.
     */
    static String describe(String path, String methodName, String message) {
        String indices = indicesIn(path, methodName);
        return indices.isEmpty() ? message : indices + " " + message;
    }

    private static String indicesIn(String path, String methodName) {
        if (path == null)
            return "";
        // anything before the method's own node belongs to the path of a cascade we did not ask for
        int start = path.indexOf(methodName);
        StringBuilder indices = new StringBuilder();
        for (int i = (start < 0 ? 0 : start); i < path.length(); i++) {
            if (path.charAt(i) != '[')
                continue;
            int end = path.indexOf(']', i);
            if (end < 0)
                break;
            indices.append(path, i, end + 1);
            i = end;
        }
        return indices.length() == 0 ? "" : "element " + indices + ":";
    }
}
