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
    private volatile long lastCheckTime = now();
    private List<WatchableFile> watchableFiles = new ArrayList<WatchableFile>();

    private static class WatchableFile {
        private final File file;
        private long lastModifiedTime;

        WatchableFile(File file) {
            this.file = file;
            this.lastModifiedTime = file.lastModified();
        }

        public boolean isChanged() {
            long lastModifiedTimeNow = file.lastModified();
            boolean changed = lastModifiedTime != lastModifiedTimeNow;
            if (changed)
                lastModifiedTime = lastModifiedTimeNow;
            return changed;
        }
    }

    HotReloadLogic(Class<? extends Config> clazz, ConfigURLStreamHandler handler, VariablesExpander expander, PropertiesManager manager) {
        this.handler = handler;
        this.manager = manager;
        HotReload hotReload = clazz.getAnnotation(HotReload.class);
        type = hotReload.type();
        interval = hotReload.unit().toMillis(hotReload.value());
        setupWatchableResources(clazz.getAnnotation(Sources.class), expander);
    }

    private void setupWatchableResources(Sources sources, VariablesExpander expander) {
        String[] values = sources.value();
        for (String value : values)
            try {
                URL url = new URL(null, expander.expand(value), handler);
                File file = Util.fileFromURL(url);
                if (file != null)
                    watchableFiles.add(new WatchableFile(file));
            } catch (MalformedURLException e) {
                ignore();
            }
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
            for (WatchableFile resource : watchableFiles)
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
