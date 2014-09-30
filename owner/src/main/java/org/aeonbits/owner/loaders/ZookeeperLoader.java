package org.aeonbits.owner.loaders;

import sun.net.www.protocol.zk.ZookeeperConnection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

/**
 * Author: Koray Sariteke
 */
public class ZookeeperLoader implements Loader {
    public boolean accept(URL url) {
        return url.getFile().toLowerCase().endsWith(".zk");
    }

    public void load(Properties result, InputStream input) throws IOException {
        ZookeeperConnection.ZookeeperStream zkStream = (ZookeeperConnection.ZookeeperStream) input;

        for (Map.Entry<String, String> entry : zkStream.pairs().entrySet()) {
               result.put(entry.getKey(), entry.getValue());
        }
    }

    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + ".zk";
    }


}
