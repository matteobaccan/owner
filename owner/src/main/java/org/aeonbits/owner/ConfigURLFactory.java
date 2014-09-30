/*
 * Copyright (c) 2012-2014, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.apache.curator.utils.ZKPaths;
import sun.net.www.protocol.zk.Handler;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Luigi R. Viggiano
 */
class ConfigURLFactory {

    private static final String CLASSPATH_PROTOCOL = "classpath:";
    private static final String ZOOKEEPER_PROTOCOL = "zk:";
    private static final String ZOOKEEPER_HOST = "zookeeper.host";
    private static final String ZOOKEEPER_PORT = "zookeeper.port";
    private static final String ZOOKEEPER_NODE_ROOT = "zookeeper.node.root";

    private final transient ClassLoader classLoader;
    private final VariablesExpander expander;

    ConfigURLFactory(ClassLoader classLoader, VariablesExpander expander) {
        this.classLoader = classLoader;
        this.expander = expander;
    }

    URL newURL(String spec) throws MalformedURLException {
        String expanded = expand(spec);
        URL url;
        if (expanded.startsWith(ZOOKEEPER_PROTOCOL)) {
            String host = System.getProperty(ZOOKEEPER_HOST);
            Integer port = System.getProperty(ZOOKEEPER_PORT) == null ? null : Integer.valueOf(System.getProperty(ZOOKEEPER_PORT));
            String rootNode = System.getProperty(ZOOKEEPER_NODE_ROOT);
            if (null == host || null == port || null == rootNode) {
                return null;
            }

            String path = ZKPaths.makePath(rootNode, expanded.substring(ZOOKEEPER_PROTOCOL.length()));
            return new URL(ZOOKEEPER_PROTOCOL, host, port, path, new Handler());
        } else if (expanded.startsWith(CLASSPATH_PROTOCOL)) {
            String path = expanded.substring(CLASSPATH_PROTOCOL.length());
            url = classLoader.getResource(path);
            if (url == null)
                return null;
        } else {
            url = new URL(expanded);
        }
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), expand(url.getPath()));
    }

    private String expand(String path) {
        return expander.expand(path);
    }

    String toClasspathURLSpec(String name) {
        return CLASSPATH_PROTOCOL + name.replace('.', '/');
    }

}
