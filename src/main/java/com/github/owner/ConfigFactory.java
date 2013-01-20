/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package com.github.owner;

import com.github.owner.Config.Sources;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static java.lang.reflect.Proxy.newProxyInstance;
import static com.github.owner.ConfigURLStreamHandler.CLASSPATH_PROTOCOL;

/**
 * Factory class to instantiate {@link Config} instances. By default a {link Config} sub-interface is associated to a
 * property having the same package name and class name as the interface itself.
 * <p/>
 * Method names are mapped to property names contained in the property files.
 *
 * @author Luigi R. Viggiano
 */
public class ConfigFactory {
    private static final SystemVariablesExpander expander = new SystemVariablesExpander();

    @SuppressWarnings("unchecked")
    public static <T extends Config> T create(Class<? extends Config> clazz) {
        Class<?>[] interfaces = new Class<?>[]{clazz};
        InvocationHandler handler = new PropertyInvocationHandler(loadPropertiesFor(clazz));
        return (T) newProxyInstance(clazz.getClassLoader(), interfaces, handler);
    }

    static Properties loadPropertiesFor(Class<? extends Config> clazz) {
        ConfigURLStreamHandler handler = new ConfigURLStreamHandler(clazz.getClassLoader(), expander);
        try {
            InputStream stream = getStreamFor(clazz, handler);
            return load(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties load(InputStream stream) throws IOException {
        Properties props = new Properties();
        if (stream != null) {
            try {
                props.load(stream);
            } finally {
                stream.close();
            }
        }
        return props;
    }

    static InputStream getStreamFor(Class<? extends Config> clazz, ConfigURLStreamHandler handler) throws IOException {
        Sources sources = clazz.getAnnotation(Sources.class);
        if (sources == null)
            return getDefaultResourceStream(clazz, handler);
        else
            return getResourceStreamBySourceAnnotation(sources, handler);
    }

    private static InputStream getDefaultResourceStream(Class<? extends Config> clazz,
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
