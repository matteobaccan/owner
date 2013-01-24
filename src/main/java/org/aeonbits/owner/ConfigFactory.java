/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.aeonbits.owner.Config.Sources;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Properties;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.aeonbits.owner.ConfigURLStreamHandler.CLASSPATH_PROTOCOL;
import static org.aeonbits.owner.PropertiesMapper.defaults;

/**
 * Factory class to instantiate {@link Config} instances. By default a {link Config} sub-interface is associated to a
 * property having the same package name and class name as the interface itself.
 * <p/>
 * Method names are mapped to property names contained in the property files.
 *
 * @author Luigi R. Viggiano
 */
public abstract class ConfigFactory {
    private static final SystemVariablesExpander expander = new SystemVariablesExpander();


    ConfigFactory() {
        throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
    }

    /**
     * Creates a {@link Config} instance from the specified interface
     *
     * @param clazz     the interface extending from {@link Config} that you want to instantiate.
     * @param imports   additional variables to be used to resolve the properties.
     * @param <T>       type of the interface.
     * @return  an object implementing the given interface, which maps methods to property values.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Config> T create(Class<? extends T> clazz, Map<?, ?>... imports) {
        Class<?>[] interfaces = new Class<?>[]{clazz};
        InvocationHandler handler = new PropertiesInvocationHandler(loadPropertiesFor(clazz, imports));
        return (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }

    static Properties loadPropertiesFor(Class<? extends Config> clazz, Map<?, ?>... imports) {
        ConfigURLStreamHandler handler = new ConfigURLStreamHandler(clazz.getClassLoader(), expander);
        try {
            InputStream stream = getStreamFor(clazz, handler);
            Properties defaults = defaults(clazz);
            return load(stream, defaults, imports);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties load(InputStream stream, Properties defaults, Map<?, ?>... imports) throws IOException {
        Properties props = new Properties(defaults);
        addImports(props, imports);

        if (stream != null) {
            try {
                props.load(stream);
            } finally {
                stream.close();
            }
        }
        return props;
    }

    private static void addImports(Properties props, Map<?, ?>... imports) {
        for (Map<?, ?> importItem : imports)
            props.putAll(importItem);
    }

    static InputStream getStreamFor(Class<?> clazz, ConfigURLStreamHandler handler) throws IOException {
        Sources sources = clazz.getAnnotation(Sources.class);
        if (sources == null)
            return getDefaultResourceStream(clazz, handler);
        else
            return getResourceStreamBySourceAnnotation(sources, handler);
    }

    private static InputStream getDefaultResourceStream(Class<?> clazz,
                                                        ConfigURLStreamHandler handler) throws IOException {
        String spec = CLASSPATH_PROTOCOL + ":" + clazz.getName().replace('.', '/') + ".properties";
        return getInputStream(new URL(null, spec, handler));
    }

    private static InputStream getResourceStreamBySourceAnnotation(Sources sources,
                                                                   ConfigURLStreamHandler handler) throws
            MalformedURLException {

        String[] values = sources.value();

        for (String source : values) {
            URL url = new URL(null, source, handler);
            try {
                InputStream stream = getInputStream(url);
                if (stream != null)
                    return stream;
            } catch (IOException ex) {
                // ignore: happens when a file specified in the sources is not found or cannot be read.
            }
        }
        return null;
    }

    private static InputStream getInputStream(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        if (conn == null)
            return null;
        return conn.getInputStream();
    }
}
