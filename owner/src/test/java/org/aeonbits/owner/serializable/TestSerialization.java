/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.serializable;

import org.aeonbits.owner.Config.HotReload;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import org.aeonbits.owner.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static java.io.File.createTempFile;
import static org.aeonbits.owner.util.Collections.map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Luigi R. Viggiano
 */
public class TestSerialization implements TestConstants {
    private static final String PROPERTY_FILE_NAME = "AsyncAutoReloadConfig.properties";

    private static final String SPEC = "file:"+ RESOURCES_DIR + "/" + PROPERTY_FILE_NAME;

    private File target;

    @HotReload
    @Sources(SPEC)
    public static interface MyConfig extends Mutable {
        @DefaultValue("someText")
        public String someText();

        @DefaultValue("some,array")
        public String[] someArray();
    }

    @Before
    public void before() throws IOException {
        File parent = new File(RESOURCES_DIR);
        parent.mkdirs();
        target = createTempFile("TestSerialization", ".ser", parent);
    }

    @After
    public void after() throws IOException {
        target.delete();
    }

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        MyConfig cfg = ConfigFactory.create(MyConfig.class, map("foo", "bar"));
        assertEquals("someText", cfg.someText());
        assertArrayEquals(new String[] {"some", "array"}, cfg.someArray());
        cfg.addPropertyChangeListener("someText", new MyPropertyChangeListener());

        serialize(cfg, target);

        MyConfig deserialized = deserialize(target);

        assertEquals(cfg, deserialized);
    }

    private MyConfig deserialize(File target) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(target);
        try {
            ObjectInputStream oin = new ObjectInputStream(fin);
            try {
                return (MyConfig) oin.readObject();
            } finally {
                oin.close();
            }
        } finally {
            fin.close();
        }
    }

    private void serialize(MyConfig cfg, File target) throws IOException {
        FileOutputStream fout = new FileOutputStream(target);
        try {
            ObjectOutputStream oout = new ObjectOutputStream(fout);
            try {
                oout.writeObject(cfg);
                oout.flush();
            } finally {
                oout.close();
            }
        } finally {
            fout.close();
        }
    }

    private static class MyPropertyChangeListener implements PropertyChangeListener, Serializable {
        public void propertyChange(PropertyChangeEvent evt) {
        }
    }
}
