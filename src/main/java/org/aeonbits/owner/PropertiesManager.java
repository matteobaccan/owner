/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;


import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;
import org.aeonbits.owner.event.RollbackException;
import org.aeonbits.owner.event.TransactionalPropertyChangeListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Collections.synchronizedList;
import static org.aeonbits.owner.Config.LoadType.FIRST;
import static org.aeonbits.owner.PropertiesMapper.defaults;
import static org.aeonbits.owner.Util.asString;
import static org.aeonbits.owner.Util.ignore;
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
    private final List<URL> urls;
    private final ConfigURLFactory urlFactory;
    private HotReloadLogic hotReloadLogic = null;

    private volatile boolean loading = false;

    private final List<ReloadListener> reloadListeners = synchronizedList(new LinkedList<ReloadListener>());
    private Object proxy;
    private final LoadersManager loaders;
    private List<PropertyChangeListener> propertyChangeListeners =
            synchronizedList(new LinkedList<PropertyChangeListener>());

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

    private List<URL> toURLs(Sources sources) {
        String[] specs = specs(sources);
        ArrayList<URL> result = new ArrayList<URL>();
        for (String spec : specs) {
            try {
                URL url = urlFactory.newURL(spec);
                if (url != null)
                    result.add(url);
            } catch (MalformedURLException e) {
                throw unsupported(e, "Can't convert '%s' to a valid URL", spec);
            }
        }
        return result;
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
        } finally {
            loading = false;
            writeLock.unlock();
        }
    }

    @Delegate
    public void reload() {
        writeLock.lock();
        try {
            doClear();
            load();
            fireReloadEvent();
        } finally {
            writeLock.unlock();
        }
    }

    private void fireReloadEvent() {
        for (ReloadListener listener : reloadListeners)
            listener.reloadPerformed(new ReloadEvent(proxy));
    }

    @Delegate
    public void addReloadListener(ReloadListener listener) {
        reloadListeners.add(listener);
    }

    @Delegate
    public void removeReloadListener(ReloadListener listener) {
        reloadListeners.remove(listener);
    }

    @Delegate
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeListeners.add(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeListeners.remove(listener);
    }

    Properties doLoad() {
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
    public void storeToXML(OutputStream os, String comment) throws IOException {
        readLock.lock();
        try {
            properties.storeToXML(os, comment);
        } finally {
            readLock.unlock();
        }
    }

    @Delegate
    public Set<String> propertyNames() {
        readLock.lock();
        try {
            LinkedHashSet<String> result = new LinkedHashSet<String>();
            for (Enumeration<?> propertyNames = properties.propertyNames(); propertyNames.hasMoreElements(); )
                result.add((String) propertyNames.nextElement());
            return result;
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
            PropertyChangeEvent event = createPropertyChangeEvent(key, value);
            try {
                fireBeforePropertyChange(event);
                if (value == null) return removeProperty(key);
                String result = asString(properties.setProperty(key, value));
                firePropertyChange(event);
                return result;
            } catch (RollbackException e) {
                return properties.getProperty(key);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void firePropertyChange(PropertyChangeEvent event) {
        for (PropertyChangeListener listener : propertyChangeListeners)
            listener.propertyChange(event);
    }

    private void fireBeforePropertyChange(PropertyChangeEvent event) throws RollbackException {
        for (PropertyChangeListener listener : propertyChangeListeners)
            if (listener instanceof TransactionalPropertyChangeListener)
                ((TransactionalPropertyChangeListener) listener).beforePropertyChange(event);
    }

    private PropertyChangeEvent createPropertyChangeEvent(String propertyName, String newValue) {
        String oldValue = properties.getProperty(propertyName);
        return new PropertyChangeEvent(proxy, propertyName, oldValue, newValue);
    }


    @Delegate
    public String removeProperty(String key) {
        writeLock.lock();
        try {
            PropertyChangeEvent event = createPropertyChangeEvent(key, null);
            try {
                fireBeforePropertyChange(event);
                String result = asString(properties.remove(key));
                firePropertyChange(event);
                return result;
            } catch (RollbackException e) {
                return properties.getProperty(key);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Delegate
    public void clear() {
        writeLock.lock();
        try {

            Set<Object> keys = properties.keySet();
            List<PropertyChangeEvent> events = new ArrayList<PropertyChangeEvent>();
            for (Object key : keys) {
                PropertyChangeEvent event = createPropertyChangeEvent((String) key, null);
                fireBeforePropertyChange(event);
                events.add(event);
            }

            doClear();

            for (PropertyChangeEvent event : events)
                firePropertyChange(event);

        } catch (RollbackException e) {
            ignore();
        } finally {
            writeLock.unlock();
        }
    }

    private void doClear() {
        properties.clear();
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
