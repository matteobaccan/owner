/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

/**
 * How a tree becomes flat property keys, in one place.
 *
 * <p>
 * A {@link Loader} hands back a {@link java.util.Properties}, which is a flat map of string to string, and
 * XML, JSON, YAML and the rest are trees. Every one of them therefore has to say the same two things: how a
 * child is named below its parent, and how one element of a list is named. If each loader answered on its
 * own the answers would drift, and a key would mean something slightly different according to the file it
 * came out of. They answer here instead.
 * </p>
 *
 * <pre>
 *     {"server": {"host": "localhost", "ports": [80, 443]}}
 * </pre>
 * <pre>
 *     server.host=localhost
 *     server.ports[0]=80
 *     server.ports[1]=443
 * </pre>
 *
 * <p>
 * A dot for nesting, because that is the convention properties files have always used and the one
 * <code>@Key("server.host")</code> already expects. Square brackets for an index, because the dot is taken:
 * a method returning a {@link java.util.Map} reads everything under <code>server.</code> as a group, so
 * <code>ports.0</code> would make one layout of keys mean two things according only to the return type, and
 * would leave a map whose keys are numbers indistinguishable from a list.
 * </p>
 *
 * <p>
 * The two compose in both directions, which is what makes the convention worth having rather than merely
 * having one: <code>servers[0].host</code> is the host of the first server, and <code>grid[0][1]</code> is
 * a cell of a list of lists. Reading the first of those needs nested configuration interfaces and does not
 * work yet, but a loader written today produces the key that will be read then, and nothing has to be
 * flattened twice.
 * </p>
 *
 * <div><b>A dot inside a name is ambiguous, and is left so on purpose.</b> A JSON object
 * <code>{"a.b": 1}</code> nested under <code>x</code> flattens to <code>x.a.b=1</code>, which is what
 * <code>{"a": {"b": 1}}</code> flattens to as well. Escaping would remove the ambiguity for whoever wants
 * to reverse the flattening, and cost every reader of a perfectly ordinary key the escape:
 * <code>@Key("x.a.b")</code> works today for either file and would have to become something less obvious.
 * Since nothing in the library reverses a flattened key — a configuration method names the key it wants,
 * and gets it — the ambiguity has no victim, and inventing a quoting scheme to serve a reader that does not
 * exist would be the worse trade. It is written down here so the choice is visible when it stops being
 * true.</div>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class PropertyKeys {

    /** Between a parent and its child. */
    public static final char NESTING = '.';

    /** Around the index of an element. */
    public static final char INDEX_OPEN = '[';

    /** @see #INDEX_OPEN */
    public static final char INDEX_CLOSE = ']';

    /** Don't let anyone instantiate this class */
    private PropertyKeys() {}

    /**
     * The key of a child below its parent: <code>server</code> and <code>host</code> give
     * <code>server.host</code>.
     *
     * @param parent the key of the parent, or <code>null</code> or empty at the top of the tree.
     * @param name   the name of the child.
     * @return the key of the child.
     */
    public static String child(String parent, String name) {
        if (parent == null || parent.isEmpty())
            return name;
        return parent + NESTING + name;
    }

    /**
     * The key of one element of a list: <code>ports</code> and <code>0</code> give <code>ports[0]</code>.
     * Elements are numbered from zero and consecutively; a gap is refused when the list is read back.
     *
     * @param key   the key of the list.
     * @param index the position of the element, from zero.
     * @return the key of the element.
     * @throws IllegalArgumentException if the index is negative.
     */
    public static String element(String key, int index) {
        if (index < 0)
            throw new IllegalArgumentException("An element index cannot be negative, and " + index + " is");
        return key + INDEX_OPEN + index + INDEX_CLOSE;
    }
}
