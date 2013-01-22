/*
 * Copyright (c) 2012, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Handler for URLs used to indicate {@link Config.Sources Sources} from which to load the properties.
 * It implements also system variables expander, using values from {@link System#getProperties()} and
 * {@link System#getenv()}.
 * The <tt>~</tt> (tilde) character is expanded as <tt>${user.home}</tt>.
 *
 * Some sample of valid URL handled by this class are:
 * <ul>
 *     <li>classpath:com.foo.bar.FooBar.properties</li>
 *     <li>file:${user.dir}/src/test/resources/test.properties</li>
 * </ul>
 *
 * This class implements by its own the classpath: protocol, other protocols are handled by delegating to default by
 * default {@link java.net.URL#URL(String, String, int, String) URL} class.
 * So you can also use http or ftp - for instance - to indicate the {@link Config.Sources Sources} for a property to
 * load, as well as any other supported by {@link java.net.URL} class.
 *
 * @author Luigi R. Viggiano
 */
class ConfigURLStreamHandler extends URLStreamHandler {
    private final ClassLoader classLoader;
    private final SystemVariablesExpander expander;
    public static final String CLASSPATH_PROTOCOL = "classpath";

    public ConfigURLStreamHandler(ClassLoader classLoader, SystemVariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        String protocol = url.getProtocol();
        String path = url.getPath();
        if (CLASSPATH_PROTOCOL.equals(protocol)) {
            URL resourceUrl = classLoader.getResource(expand(path));
            if (resourceUrl == null) return null;
            return resourceUrl.openConnection();
        } else {
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), expand(path)).openConnection();
        }
    }

    private String expand(String path) {
        return expander.expand(path);
    }
}
