/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Security regression tests for {@link XMLLoader}, covering XXE (XML External Entity)
 * and entity expansion (billion laughs) hardening.
 *
 * @author Luigi R. Viggiano
 */
public class XMLLoaderTest {

    private static final String SECRET = "TOP_SECRET_XXE_VALUE";

    private static File writeTempFile(String prefix, String suffix, String content) throws IOException {
        File file = Files.createTempFile(prefix, suffix).toFile();
        file.deleteOnExit();
        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    /**
     * An external general entity pointing at a local file must NOT be resolved:
     * its content must never leak into the parsed properties.
     */
    @Test
    public void testExternalEntityIsNotResolved() throws IOException {
        File secret = writeTempFile("owner-secret", ".txt", SECRET);

        String xml =
                "<?xml version=\"1.0\"?>" +
                "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"" + secret.toURI() + "\"> ]>" +
                "<foo>&xxe;</foo>";
        File xmlFile = writeTempFile("owner-xxe", ".xml", xml);

        Properties props = new Properties();
        try {
            new XMLLoader().load(props, xmlFile.toURI());
        } catch (IOException expectedOnStrictParsers) {
            // Some parsers abort when external entities are disabled: also acceptable.
        }

        for (Object value : props.values())
            assertFalse("XXE external entity was resolved: leaked secret in " + value,
                    String.valueOf(value).contains(SECRET));
    }

    /**
     * A recursive-entity ("billion laughs") payload must not be expanded: secure
     * processing has to abort parsing instead of exploding memory/CPU.
     */
    @Test
    public void testEntityExpansionIsLimited() throws IOException {
        String xml =
                "<?xml version=\"1.0\"?>" +
                "<!DOCTYPE lolz [" +
                "<!ENTITY lol \"lol\">" +
                "<!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">" +
                "<!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;\">" +
                "<!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">" +
                "<!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">" +
                "<!ENTITY lol5 \"&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;\">" +
                "<!ENTITY lol6 \"&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;\">" +
                "<!ENTITY lol7 \"&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;\">" +
                "<!ENTITY lol8 \"&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;\">" +
                "<!ENTITY lol9 \"&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;\">" +
                "]>" +
                "<lolz>&lol9;</lolz>";
        File xmlFile = writeTempFile("owner-lol", ".xml", xml);

        Properties props = new Properties();
        try {
            new XMLLoader().load(props, xmlFile.toURI());
            fail("expected parsing to abort due to entity expansion limits");
        } catch (IOException expected) {
            // expected: secure processing caps entity expansion and aborts.
        }
    }
}
