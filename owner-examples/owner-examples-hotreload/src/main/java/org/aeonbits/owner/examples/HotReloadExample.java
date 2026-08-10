/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
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
import org.aeonbits.owner.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Properties;

/**
 * @author Luigi R. Viggiano
 */
public class HotReloadExample {
    private static final String CFG_FILE = "file:target/examples-generated-resources/HotReloadExample.properties";
    private static File target;

    static {
        try {
            target = Util.fileFromURI(CFG_FILE);
        } catch (URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Sources(CFG_FILE)
    @HotReload(1)
    interface AutoReloadConfig extends Config, Reloadable {
        @DefaultValue("5")
        Integer someValue();
    }

    /**
     * Runs the hot reload example.
     *
     * @param args command line arguments (unused).
     * @throws IOException          if the example properties file cannot be written.
     * @throws InterruptedException if the polling loop is interrupted while sleeping.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.printf("%n%n HOT RELOAD EXAMPLE %n%n");

        Properties props = new Properties();
        props.setProperty("someValue", "10");
        save(target, props);

        AutoReloadConfig cfg = ConfigFactory.create(AutoReloadConfig.class);

        cfg.addReloadListener(event ->
                System.out.print("\rReload intercepted at " + LocalTime.now(ZoneId.systemDefault()) + " \n"));

        System.out.println("The program is running. ");

        System.out.println("Now you can change the file located at: \n\n\t" + target.getAbsolutePath() +
                           "\n\n ...and see the changes reflected below\n\n");
        int someValue = 0;
        while (someValue >= 0) {
            someValue = cfg.someValue();
            System.out.print("\rsomeValue is: " + someValue + "\t\t\t\t");
            Thread.sleep(500);
        }
    }

    private static void save(File target, Properties props) throws IOException {
        target.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(target)) {
            props.store(out, "example configuration");
        }
    }
}
