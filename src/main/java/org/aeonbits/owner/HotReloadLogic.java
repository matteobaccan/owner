/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.HotReloadType;
import org.aeonbits.owner.Config.Sources;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.Config.HotReloadType.SYNC;
import static org.aeonbits.owner.Util.ignore;
import static org.aeonbits.owner.Util.now;

/**
 * @author Luigi R. Viggiano
 */
class HotReloadLogic {
    private final ConfigURLStreamHandler handler;
    private final PropertiesManager manager;
    private final long interval;
    private final HotReloadType type;
    private volatile long lastCheckTime = 0L;
    private boolean initialized = false;
    private List<WatchableResource> watchableResources = new ArrayList<WatchableResource>();

    private static class WatchableResource {
        private final URL url;
        private long lastModifiedTime;

        WatchableResource(URL url) {
            this.url = url;
            this.lastModifiedTime = getLastModifiedTime(url);
        }

        public boolean isChanged() {
            long lastModifiedTimeNow = getLastModifiedTime(url);
            boolean changed = lastModifiedTime != lastModifiedTimeNow;
            if (changed)
                lastModifiedTime = lastModifiedTimeNow;
            return changed;
        }

        private static long getLastModifiedTime(URL url) {
            File file = Util.fileFromURL(url);
            if (file == null)
                return 0;
            return file.lastModified();
        }

        public static boolean isWatchable(URL url) {
            return Util.isFileURL(url);
        }
    }

    HotReloadLogic(Class<? extends Config> clazz, ConfigURLStreamHandler handler, PropertiesManager manager) {
        this.handler = handler;
        this.manager = manager;
        HotReload hotReload = clazz.getAnnotation(HotReload.class);
        type = hotReload.type();
        interval = hotReload.unit().toMillis(hotReload.value());
        setupWatchableResources(clazz.getAnnotation(Sources.class));
    }

    private void setupWatchableResources(Sources sources) {
        String[] values = sources.value();
        for (String value : values)
            try {
                URL url = new URL(null, value, handler);
                if (WatchableResource.isWatchable(url))
                    watchableResources.add(new WatchableResource(url));
            } catch (MalformedURLException e) {
                ignore();
            }
    }

    synchronized void init(long lastLoadTime) {
        if (! initialized) {
            lastCheckTime = lastLoadTime;
            initialized = true;
        }
    }

    void checkAndReload() {
        if (needsReload())
            manager.reload();
    }

    private synchronized boolean needsReload() {
        if (manager.loading || ! initialized()) return false;

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

    private boolean initialized() {
        return lastCheckTime > 0;
    }

    boolean isAsync() {
        return type == ASYNC;
    }


    boolean isSync() {
        return type == SYNC;
    }
}
