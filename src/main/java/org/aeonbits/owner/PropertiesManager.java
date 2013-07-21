/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;


import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.aeonbits.owner.Config.LoadType;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.PropertiesMapper.defaults;
import static org.aeonbits.owner.Util.asString;
import static org.aeonbits.owner.Util.reverse;
import static org.aeonbits.owner.Util.unsupported;

/**
 * Loads properties and manages access to properties handling concurrency.
 *
 * @author Luigi R. Viggiano
 */
class PropertiesManager implements Reloadable, Accessible, Mutable {
    private final Class<? extends Config> clazz;
    private final Map<?, ?>[] imports;
    private final Properties properties;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock readLock = lock.readLock();
    private final WriteLock writeLock = lock.writeLock();

    private final LoadType loadType;
    private final ArrayList<URL> urls;
    private final ConfigURLFactory urlFactory;
    private HotReloadLogic hotReloadLogic = null;

    private volatile boolean loading = false;

    private List<ReloadListener> reloadListeners = Collections.synchronizedList(new LinkedList<ReloadListener>());
    private Object proxy;
    private final LoadersManager loaders;

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Delegate {
    }

    PropertiesManager(Class<? extends Config> clazz, Properties properties, ScheduledExecutorService scheduler,
                      VariablesExpander expander, LoadersManager loaders, Map<?, ?>... imports) {
        this.clazz = clazz;
        this.properties = properties;
        this.loaders = loaders;
        this.imports = imports;

        urlFactory = new ConfigURLFactory(clazz.getClassLoader(), expander);
        urls = toURLs(clazz.getAnnotation(Sources.class));

        LoadPolicy loadPolicy = clazz.getAnnotation(LoadPolicy.class);
        loadType = (loadPolicy != null) ? loadPolicy.value() : FIRST;

        setupHotReload(clazz, scheduler);
    }

    ArrayList<URL> toURLs(Sources sources) {
        String[] specs = specs(sources);
        ArrayList<URL> urls = new ArrayList<URL>();
        for (String spec : specs) {
            try {
                URL url = urlFactory.newURL(spec);
                if (url != null)
                    urls.add(url);
            } catch (MalformedURLException e) {
                throw unsupported(e, "Can't convert '%s' to a valid URL", spec);
            }
        }
        return urls;
    }

    private String[] specs(Sources sources) {
        if (sources != null) return sources.value();
        return defaultSpecs();
    }

    private String[] defaultSpecs() {
        String prefix = urlFactory.toClasspathURLSpec(clazz.getName());
        return new String[] {prefix + ".properties", prefix + ".xml"};
    }

    private void setupHotReload(Class<? extends Config> clazz, ScheduledExecutorService scheduler) {
        HotReload hotReload = clazz.getAnnotation(HotReload.class);
        if (hotReload != null) {
            hotReloadLogic = new HotReloadLogic(hotReload, urls, this);

            if (hotReloadLogic.isAsync())
                scheduler.scheduleAtFixedRate(new Runnable() {
                    public void run() {
                        hotReloadLogic.checkAndReload();
                    }
                }, hotReload.value(), hotReload.value(), hotReload.unit());
        }
    }

    Properties load() {
        writeLock.lock();
        try {
            loading = true;
            defaults(properties, clazz);
            Properties loadedFromFile = doLoad();
            merge(properties, loadedFromFile);
            merge(properties, reverse(imports));
            return properties;
        } catch (IOException e) {
            throw unsupported(e, "Properties load failed");
        } finally {
            loading = false;
            writeLock.unlock();
        }
    }

    @Delegate
    public void reload() {
        writeLock.lock();
        try {
            clear();
            load();
            for (ReloadListener listener : reloadListeners)
                listener.reloadPerformed(new ReloadEvent(proxy));
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void addReloadListener(ReloadListener listener) {
        reloadListeners.add(listener);
    }

    @Delegate
    public void removeReloadListener(ReloadListener listener) {
        reloadListeners.remove(listener);
    }

    Properties doLoad() throws IOException {
        return loadType.load(urls, loaders);
    }

    private static void merge(Properties results, Map<?, ?>... inputs) {
        for (Map<?, ?> input : inputs)
            results.putAll(input);
    }

    @Delegate
    public String getProperty(String key) {
        readLock.lock();
        try {
            return properties.getProperty(key);
        } finally {
            readLock.unlock();
        }
    }

    void syncReloadCheck() {
        if (hotReloadLogic != null && hotReloadLogic.isSync())
            hotReloadLogic.checkAndReload();
    }

    @Delegate
    public String getProperty(String key, String defaultValue) {
        readLock.lock();
        try {
            return properties.getProperty(key, defaultValue);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void list(PrintStream out) {
        readLock.lock();
        try {
            properties.list(out);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void list(PrintWriter out) {
        readLock.lock();
        try {
            properties.list(out);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public void store(OutputStream out, String comments) throws IOException {
        readLock.lock();
        try {
            properties.store(out, comments);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public String setProperty(String key, String value) {
        writeLock.lock();
        try {
            if (value == null) return removeProperty(key);
            return asString(properties.setProperty(key, value));
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public String removeProperty(String key) {
        writeLock.lock();
        try {
            return asString(properties.remove(key));
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void clear() {
        writeLock.lock();
        try {
            properties.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void load(InputStream inStream) throws IOException {
        writeLock.lock();
        try {
            properties.load(inStream);
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void load(Reader reader) throws IOException {
        writeLock.lock();
        try {
            properties.load(reader);
        } finally {
            writeLock.unlock();
        }
    }

    public void setProxy(Object proxy) {
        if (this.proxy == null)
            this.proxy = proxy;
    }

    @Delegate
    @Override
    public String toString() {
        readLock.lock();
        try {
            return properties.toString();
        } finally {
            readLock.unlock();
        }
    }

    boolean isLoading() {
        return loading;
    }
}
