/*
 * Copyright (c) 2013, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.sql.Driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author luigi
 */
public class SpecialTypesTest {

    @Test
    public void testFileReturnType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        File f = config.sampleFile();
        assertNotNull(f);
        assertEquals("foobar.txt", f.getName());
    }

    @Test
    public void testURLReturnType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        URL u = config.sampleURL();
        assertNotNull(u);
        assertEquals("http://owner.aeonbits.org", u.toString());
    }

    @Test
    public void testClassWithStringConstructor() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        CustomType custom = config.customType();
        assertNotNull(custom);
        assertEquals("test", custom.getText());
    }

    @Test
    public void testClassWithObjectConstructor() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        Reference reference = config.reference();
        assertNotNull(reference);
        assertEquals("foobar", reference.toString());
    }

    @Test
    public void testCustomTypeWithParameter() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        CustomType custom = config.salutation("Luigi");
        assertNotNull(custom);
        assertEquals("Hello Luigi!", custom.getText());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInvalidCustomType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        config.invalid();
    }

    @Test
    public void testEnumType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        EnumType enumType = config.enumType();
        assertNotNull(enumType);
        assertEquals(EnumType.FOO, enumType);
    }

    @Test
    public void testClassType() throws Throwable {
        SpecialTypes config = ConfigFactory.create(SpecialTypes.class);
        Class driver = config.jdbcDriver();
        assertNotNull(driver);
        assertEquals(Driver.class, driver);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClassWithInvalidValueOfMethod() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        cfg.invalidValueOf();
    }

    @Test
    public void testClassWithValueOfMethod() throws Throwable {
        SpecialTypes cfg = ConfigFactory.create(SpecialTypes.class);
        ValueOf valueOf = cfg.valueOf();
        assertNotNull(valueOf);
        assertEquals("valueOf", valueOf.toString());
    }

}
