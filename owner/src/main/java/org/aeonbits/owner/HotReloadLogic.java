/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import static org.aeonbits.owner.Config.HotReloadType.ASYNC;
import static org.aeonbits.owner.Config.HotReloadType.SYNC;
import static org.aeonbits.owner.Util.fileFromURI;
import static org.aeonbits.owner.Util.now;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.HotReloadType;

/**
 * @author Luigi R. Viggiano
 */
class HotReloadLogic implements Serializable {

    private final PropertiesManager manager;
    private final long interval;
    private final HotReloadType type;
    private volatile long lastCheckTime = now();
    private final List<WatchableResource> watchableResources = new ArrayList<WatchableResource>();

    private static interface WatchableResource extends Serializable {
        boolean isChanged();
    }

    private static class WatchableFile implements WatchableResource {
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

    private static class WatchableSystemProperties implements WatchableResource {
        public boolean isChanged() {
            return true;
        }
    }

    public HotReloadLogic(HotReload hotReload, List<URI> uris, PropertiesManager manager) {
        this.manager = manager;
        type = hotReload.type();
        interval = hotReload.unit().toMillis(hotReload.value());
        setupWatchableResources(uris);
    }

    private void setupWatchableResources(List<URI> uris) {
        Set<File> files = new LinkedHashSet<File>();
        for (URI uri : uris) {
            if (uri.toString().equals("system:properties")) {
                watchableResources.add(new WatchableSystemProperties());
            } else {
                File file = fileFromURI(uri);
                if (file != null)
                    files.add(file);
            }
        }
        for (File file : files)
            watchableResources.add(new WatchableFile(file));
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
