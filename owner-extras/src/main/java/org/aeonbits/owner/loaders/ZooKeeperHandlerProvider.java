/*
 * Copyright (c) 2012-2017, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import sun.net.www.protocol.zookeeper.Handler;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

public class ZooKeeperHandlerProvider extends URLStreamHandlerProvider {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return new Handler();
    }
}
