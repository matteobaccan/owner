/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.examples;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Reloadable;
import org.aeonbits.owner.event.ReloadEvent;
import org.aeonbits.owner.event.ReloadListener;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

import static org.aeonbits.owner.UtilTest.save;

/**
 * @author Luigi R. Viggiano
 */
public class AutoReloadExample {
    private static final String spec = "file:target/test-resources/AutoReloadExample.properties";
    private static File target;

    @Sources(spec)
    @HotReload(1)
    interface AutoReloadConfig extends Config, Reloadable {
        @DefaultValue("5")
        Integer someValue();
    }

    static {
        try {
            target = new File(new URL(spec).getFile());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        save(target, new Properties() {{
            setProperty("someValue", "10");
        }});

        AutoReloadConfig cfg = ConfigFactory.create(AutoReloadConfig.class);
        
        cfg.addReloadListener(new ReloadListener() {
            public void reloadPerformed(ReloadEvent event) {
                System.out.print("\rReload intercepted at " + new Date() + " \n"); 
            }
        });

        System.out.println("You can change the file " + spec + " and see the changes reflected below");
        int someValue = 0;
        while (someValue >= 0) {
            someValue = cfg.someValue();
            System.out.print("\rsomeValue is: " + someValue + "\t\t\t\t");
            Thread.sleep(500);
        }

    }
}
