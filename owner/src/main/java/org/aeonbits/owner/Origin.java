/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

import static org.aeonbits.owner.util.Util.hideCredentials;

/**
 * Where a property came from: which of the {@link Config.Sources sources} produced it, or whether it was an
 * import, a {@link Config.DefaultValue} or something written at run time.
 * <p>
 * A configuration merged out of several sources answers with one value, and until now nothing said which
 * source that value came from. That is the question this answers, and it is asked in earnest: the same
 * property may be in a file and in the environment, and telling them apart is what
 * <a href="https://github.com/matteobaccan/owner/issues/277">issue #277</a> needed in order to save back
 * only what belongs to the file.
 * </p>
 * <p>
 * Obtained through {@link Traceable}, which a configuration interface extends when it wants to ask.
 * </p>
 * <p>
 * <b>The source never carries credentials.</b> A URI may hold them —
 * <code>https://user:secret@config/app.properties</code> is legal and used — and an origin handed to the
 * caller is one more place they would otherwise appear, so it is masked exactly as the log lines and the
 * exception messages are.
 * </p>
 *
 * @author Matteo Baccan
 * @since 2.0.0
 */
public final class Origin implements Serializable {

    private static final long serialVersionUID = 4022181983516572311L;

    /**
     * What kind of place a property came from. It is worth having beside the source because three of the
     * four have no source to name, and "no source" would otherwise mean three different things.
     */
    public enum Kind {

        /** One of the {@link Config.Sources}, named by {@link Origin#source()}. */
        SOURCE,

        /** One of the maps handed to {@link ConfigFactory#create(Class, java.util.Map[])}. */
        IMPORT,

        /** A {@link Config.DefaultValue} on the method: nobody wrote this property, the interface did. */
        DEFAULT_VALUE,

        /** Written after the configuration was loaded, through {@link Mutable}. */
        RUNTIME
    }

    /** Which sort of place the value came from. See {@link #kind()}. */
    private final Kind kind;
    /** Which place of that sort. See {@link #source()}. */
    private final String source;

    private Origin(Kind kind, String source) {
        this.kind = kind;
        this.source = source;
    }

    /** The origin of a property read from the given source. */
    static Origin of(URI uri) {
        return new Origin(Kind.SOURCE, hideCredentials(uri));
    }

    /** The origin of a property that came from the import at the given position. */
    static Origin ofImport(int position) {
        return new Origin(Kind.IMPORT, "import[" + position + "]");
    }

    /** The origin of a property that only the interface declares. */
    static Origin ofDefaultValue() {
        return new Origin(Kind.DEFAULT_VALUE, null);
    }

    /** The origin of a property written while the configuration was running. */
    static Origin ofRuntime() {
        return new Origin(Kind.RUNTIME, null);
    }

    /**
     * What kind of place the property came from.
     *
     * @return the kind; never <code>null</code>.
     */
    public Kind kind() {
        return kind;
    }

    /**
     * The source that produced the property, as it was written in {@link Config.Sources} and with any
     * credentials masked, or the position of the import that carried it.
     *
     * @return the source, or <code>null</code> for a {@link Kind#DEFAULT_VALUE} and for a
     *         {@link Kind#RUNTIME} write, neither of which comes from one.
     */
    public String source() {
        return source;
    }

    /**
     * Tells whether the property was written somewhere rather than declared by the interface, which is the
     * distinction the loaded properties can no longer make on their own: a default and a value read from a
     * file are the same property once they are merged.
     *
     * @return <code>true</code> unless this is a {@link Kind#DEFAULT_VALUE}.
     */
    public boolean isWritten() {
        return kind != Kind.DEFAULT_VALUE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Origin)) return false;
        Origin that = (Origin) obj;
        return this.kind == that.kind && Objects.equals(this.source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, source);
    }

    /** Meant to be read by a person: the source when there is one, and what kind of place it was otherwise. */
    @Override
    public String toString() {
        if (source != null)
            return source;
        return kind == Kind.DEFAULT_VALUE ? "@DefaultValue" : "set at run time";
    }
}
