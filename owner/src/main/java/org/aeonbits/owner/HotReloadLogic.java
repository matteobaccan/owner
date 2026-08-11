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

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.Config.HotReloadType.SYNC;
import static org.aeonbits.owner.util.Util.fileFromURI;
import static org.aeonbits.owner.util.Util.hideCredentials;
import static org.aeonbits.owner.util.Util.now;
import static org.aeonbits.owner.util.Util.system;

/**
 * @author Luigi R. Viggiano
 */
class HotReloadLogic implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(HotReloadLogic.class.getName());

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

    HotReloadLogic(HotReload hotReload, List<URI> uris, PropertiesManager manager) {
        this.manager = manager;
        type = hotReload.type();
        interval = hotReload.unit().toMillis(hotReload.value());
        setupWatchableResources(uris);
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
