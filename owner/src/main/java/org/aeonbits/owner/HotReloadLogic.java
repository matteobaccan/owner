/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.HotReloadType;
import org.aeonbits.owner.util.DurationParser;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.Config.HotReloadType.SYNC;
import static org.aeonbits.owner.util.Util.fileFromURI;
import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.now;
import static org.aeonbits.owner.util.Util.system;
import static org.aeonbits.owner.util.Util.unsupported;

/**
 * @author Luigi R. Viggiano
 */
class HotReloadLogic implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(HotReloadLogic.class.getName());

    /** A number with no unit after it, which {@link #intervalMillis} refuses rather than guess about. */
    private static final Pattern BARE_NUMBER = Pattern.compile("[+-]?\\d+");

    private final PropertiesManager manager;
    private final long interval;
    private final HotReloadType type;
    private volatile long lastCheckTime = now();
    private final List<WatchableResource> watchableResources = new ArrayList<>();

    private interface WatchableResource extends Serializable {
        boolean isChanged();
    }

    private static class WatchableFile implements WatchableResource {
        private final File file;
        private long lastModifiedTime;

        WatchableFile(File file) {
            this.file = file;
            this.lastModifiedTime = file.lastModified();
        }

        @Override
        public boolean isChanged() {
            long lastModifiedTimeNow = file.lastModified();
            boolean changed = lastModifiedTime != lastModifiedTimeNow;
            if (changed)
                lastModifiedTime = lastModifiedTimeNow;
            return changed;
        }
    }

    private static class WatchableSystemProperties implements WatchableResource {
        private final Properties props;
        private int lastHashCode;


        WatchableSystemProperties() {
            props = system().getProperties();
            lastHashCode = props.hashCode();
        }

        @Override
        public boolean isChanged() {
            int newHashCode = props.hashCode();
            boolean changed = lastHashCode != newHashCode;
            if (changed)
                lastHashCode = newHashCode;
            return changed;
        }
    }

    HotReloadLogic(HotReload hotReload, long interval, List<URI> uris, PropertiesManager manager) {
        this.manager = manager;
        this.type = hotReload.type();
        this.interval = interval;
        setupWatchableResources(uris);
    }

    /**
     * How long to wait between two checks, in milliseconds, read off the annotation.
     * <p>
     * {@link HotReload#interval()} decides it when it is set, expanded first through the same variables a
     * {@link Config.Sources} spec is expanded with, so that the interval can come from outside the source
     * file: an annotation takes constants, and five seconds in development and five minutes in production
     * used to mean two interfaces. {@link HotReload#value()} and {@link HotReload#unit()} answer when it
     * is not, which is what every existing configuration has.
     * </p>
     * <p>
     * The two are not merged: an annotation attribute cannot be told apart from its default once
     * compiled, so a <code>value</code> left out and a <code>value</code> written as 5 look the same
     * here. What can be told apart is which of the two forms was used, and that is what decides.
     * </p>
     *
     * @param hotReload the annotation as written.
     * @param clazz     the interface carrying it, for the message.
     * @param expander  the expander of the factory this configuration is being created by.
     * @return the interval in milliseconds.
     * @throws UnsupportedOperationException if the text does not read as a positive duration.
     */
    static long intervalMillis(HotReload hotReload, Class<?> clazz, VariablesExpander expander) {
        String declared = hotReload.interval().trim();
        if (declared.isEmpty())
            return hotReload.unit().toMillis(hotReload.value());

        String expanded = expander.expand(declared).trim();
        // the parser reads a bare number as milliseconds, and the attribute next door reads a bare number
        // as seconds: whoever moves @HotReload(5) to @HotReload(interval = "5") would get a check every
        // five milliseconds. Two neighbouring attributes cannot mean different things by the same digits,
        // so this one is not answered by a default
        if (BARE_NUMBER.matcher(expanded).matches())
            throw unsupported(
                    "@HotReload(interval = \"%s\") on '%s'%s carries no unit. A bare number is read as "
                            + "milliseconds here, while @HotReload(%s) means seconds: write the unit, as in "
                            + "'%ss' or '%sms'",
                    declared, clazz.getName(), expandedInto(declared, expanded),
                    expanded, expanded, expanded);

        long millis;
        try {
            millis = DurationParser.parse(expanded).toMillis();
        } catch (RuntimeException itDoesNotReadAsOne) {
            throw unsupported(itDoesNotReadAsOne,
                    "@HotReload(interval = \"%s\") on '%s'%s does not read as a duration: %s. Write the unit "
                            + "with the value, as in 500ms, 30s, 5m or PT1H30M",
                    declared, clazz.getName(), expandedInto(declared, expanded),
                    itDoesNotReadAsOne.getMessage());
        }
        if (millis <= 0)
            throw unsupported(
                    "@HotReload(interval = \"%s\") on '%s'%s is %d ms: the time between two checks has to "
                            + "be a positive amount",
                    declared, clazz.getName(), expandedInto(declared, expanded), millis);
        return millis;
    }

    private static String expandedInto(String declared, String expanded) {
        return declared.equals(expanded) ? "" : ", which expands to '" + expanded + "',";
    }

    long interval() {
        return interval;
    }

    /**
     * Works out what can be watched, and says what cannot.
     * <p>
     * Only a file and the system properties can be: watching means asking something whether it has changed
     * since it was last asked, and a resource inside a jar, an <code>http:</code> source or the environment
     * answer no such question. A source that cannot be watched is therefore dropped — there is nothing else
     * to do with it — but <b>it is not dropped in silence</b>, which is what it was until now.
     * </p>
     * <p>
     * That silence is a different thing from the one an absent source gets, and the difference is who asked.
     * A source that is missing is a fallback doing its work; here somebody wrote {@link Config.HotReload} on
     * the interface, and for this source it will never happen. "I changed the file and nothing happened" is
     * the question that follows, and the answer has to be somewhere.
     * </p>
     */
    private void setupWatchableResources(List<URI> uris) {
        Set<File> files = new LinkedHashSet<>();
        List<URI> unwatchable = new ArrayList<>();
        for (URI uri : uris) {
            if (uri.toString().equals("system:properties")) {
                watchableResources.add(new WatchableSystemProperties());
            } else {
                File file = fileFromURI(uri);
                if (file != null)
                    files.add(file);
                else
                    unwatchable.add(uri);
            }
        }
        for (File file : files)
            watchableResources.add(new WatchableFile(file));

        reportWhatCannotBeWatched(unwatchable);
        LOGGER.log(Level.CONFIG, () -> String.format(
                "%s: hot reload is on, %s, every %d ms, watching %s",
                manager.configuredClass().getName(), type == ASYNC ? "in the background" : "on access",
                interval, watched(files)));
    }

    private void reportWhatCannotBeWatched(List<URI> unwatchable) {
        if (unwatchable.isEmpty())
            return;

        List<String> named = new ArrayList<>();
        for (URI uri : unwatchable)
            named.add(hideCredentials(uri));
        LOGGER.log(Level.WARNING, () -> String.format(
                "%s asks for hot reload, and %d of its sources cannot be watched: %s. Only a file and "
                        + "'system:properties' can be, a change being something they can be asked about; a "
                        + "resource inside a jar, a remote source and the environment cannot. Those sources "
                        + "are read at every reload, but nothing about them will ever trigger one.",
                manager.configuredClass().getName(), unwatchable.size(), named));
    }

    private List<String> watched(Set<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files)
            names.add(file.getPath());
        if (watchableResources.size() > files.size())
            names.add("system:properties");
        return names;
    }

    synchronized void checkAndReload() {
        if (needsReload())
            manager.reload();
    }

    private boolean needsReload() {
        if (manager.isLoading()) return false;

        long now = now();
        if (now < lastCheckTime + interval)
            return false;

        try {
            for (WatchableResource resource : watchableResources)
                if (resource.isChanged())
                    return true;
            return false;
        } finally {
            lastCheckTime = now;
        }
    }

    boolean isAsync() {
        return type == ASYNC;
    }

    boolean isSync() {
        return type == SYNC;
    }

}
