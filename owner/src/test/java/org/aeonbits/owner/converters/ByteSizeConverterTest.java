/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.converters;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.util.bytesize.ByteSize;
import org.aeonbits.owner.util.bytesize.ByteSizeUnit;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * @author Stefan Freyr Stefansson
 */
public class ByteSizeConverterTest {
    public interface ByteSizeConfig extends Config {
        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10 byte")
        ByteSize singular10byteWithSpace();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10byte")
        ByteSize singular10byteWithoutSpace();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10 bytes")
        ByteSize plural10byte();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10m")
        ByteSize short10mebibytes();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10mi")
        ByteSize medium10mebibytes();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10mib")
        ByteSize long10mebibytes();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10 megabytes")
        ByteSize full10megabytes();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("10 sillybyte")
        ByteSize invalidSillybyte();

        @ConverterClass(ByteSizeConverter.class)
        @DefaultValue("megabyte")
        ByteSize invalidNoNumber();
    }

    @Test
    public void testValidByteSizeConverter(){
        ByteSizeConfig cfg = ConfigFactory.create(ByteSizeConfig.class);
        ByteSize bs;

        bs = cfg.plural10byte();
        assertEquals(bs, new ByteSize(10, ByteSizeUnit.BYTES));

        bs = cfg.singular10byteWithoutSpace();
        assertEquals(bs, new ByteSize(10, ByteSizeUnit.BYTES));

        bs = cfg.singular10byteWithSpace();
        assertEquals(bs, new ByteSize(10, ByteSizeUnit.BYTES));

        ByteSize compare = new ByteSize(10, ByteSizeUnit.MEBIBYTES);
        assertEquals(compare, cfg.short10mebibytes());
        assertEquals(compare, cfg.medium10mebibytes());
        assertEquals(compare, cfg.long10mebibytes());
        assertNotEquals(compare, cfg.full10megabytes());
        assertEquals(new ByteSize(10, ByteSizeUnit.MEGABYTES), cfg.full10megabytes());
    }

    @Test
    public void testInvalid() throws NoSuchMethodException, IllegalAccessException {
        ByteSizeConfig cfg = ConfigFactory.create(ByteSizeConfig.class);
        ByteSize bs;
        for (String method : new String[]{"invalidSillybyte", "invalidNoNumber"}) {
            Method m = ByteSizeConfig.class.getDeclaredMethod(method);
            try {
                bs = (ByteSize) m.invoke(cfg);
                fail(String.format("Invalid byte size [%s] should have thrown an exception. Instead we parsed: %s", method, bs));
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof IllegalArgumentException)){
                    fail("Got an unexpected exception type when calling method: " + method);
                }
            }
        }
    }

    /**
     * A no-break space between the amount and the unit survives trimming and shows nothing on screen, so the
     * message has to name the character rather than quote a value that looks entirely correct.
     *
     * @author Matteo Baccan
     */
    @Test
    public void testANoBreakSpaceInTheAmountIsNamed() {
        try {
            new ByteSizeConverter().convert(null, "10" + new String(Character.toChars(0x00A0)) + "MB");
            fail("a no-break space is not a space and should be reported as such");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.contains("NO-BREAK SPACE"));
            assertTrue(message, message.contains("U+00A0"));
        }
    }

    @Test
    public void testAnOrdinarySpaceInTheAmountIsStillJustASeparator() {
        assertEquals(new ByteSize(10, ByteSizeUnit.MEGABYTES),
                new ByteSizeConverter().convert(null, "10 MB"));
    }

}