/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A document is held to the grammar it declares, and to no other.
 * <p>
 * The five cases below are the whole of the rule, and they are one test class because the rule is only
 * intelligible as the difference between them: the same validity error means opposite things according to
 * whether the document declared a grammar, and whether that grammar was one this parser could read.
 * </p>
 *
 * @author Matteo Baccan
 */
public class XmlValidationTest {

    private static final String OWN_DTD =
            "<!DOCTYPE config [<!ELEMENT config (host)><!ELEMENT host (#PCDATA)>]>";

    private static final String JAVA_DTD =
            "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">";

    private static Properties read(String xml) throws IOException {
        return read(xml, "");
    }

    private static Properties read(String xml, String fragment) throws IOException {
        File file = Files.createTempFile("owner-xml", ".xml").toFile();
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), UTF_8)) {
            writer.write(xml);
        }
        Properties result = new Properties();
        try {
            new XMLLoader().load(result, new URI(file.toURI() + fragment));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return result;
    }

    // -------------------------------------------------------------------------------------------------
    // no grammar declared: there is nothing to hold the document to
    // -------------------------------------------------------------------------------------------------

    /**
     * A validating parser reports a validity error for <b>every</b> document without a DOCTYPE — "no
     * grammar found" — which says nothing about the document and everything about what was asked of the
     * parser. Ignoring exactly that one is what makes reading ordinary XML possible at all.
     */
    @Test
    public void aDocumentWithNoDoctypeIsReadAsItIs() throws IOException {
        Properties props = read("<config><host>alpha</host><port>8080</port></config>");

        assertEquals("alpha", props.getProperty("config.host"));
        assertEquals("8080", props.getProperty("config.port"));
    }

    // -------------------------------------------------------------------------------------------------
    // a grammar of the document's own
    // -------------------------------------------------------------------------------------------------

    @Test
    public void aDocumentThatKeepsToItsOwnGrammarIsRead() throws IOException {
        Properties props = read(OWN_DTD + "<config><host>alpha</host></config>");

        assertEquals("alpha", props.getProperty("config.host"));
    }

    /**
     * The one that changed in 2.0.0. The DTD says <code>config</code> contains only <code>host</code>, and
     * the document puts a <code>port</code> in it: what used to come back was the whole document, the
     * forbidden element included, with nothing said. A recoverable error does not stop the parse, so it was
     * never a truncated document — it was a complete one that its own grammar declares illegal.
     */
    @Test
    public void aDocumentThatBreaksItsOwnGrammarIsRefused() {
        try {
            read(OWN_DTD + "<config><host>alpha</host><port>8080</port></config>");
            fail("a document was read past its own grammar");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("port"));
        }
    }

    // -------------------------------------------------------------------------------------------------
    // the Java properties grammar, which is somebody else's but still the document's own choice
    // -------------------------------------------------------------------------------------------------

    @Test
    public void theJavaPropertiesFormatIsReadWhenItKeepsToItsDtd() throws IOException {
        Properties props = read(JAVA_DTD + "<properties><entry key=\"host\">alpha</entry></properties>");

        assertEquals("alpha", props.getProperty("host"));
    }

    @Test
    public void theJavaPropertiesFormatIsRefusedWhenItDoesNot() {
        try {
            read(JAVA_DTD + "<properties><entry key=\"host\">alpha</entry><bogus/></properties>");
            fail("a properties document was read past its own DTD");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("bogus"));
        }
    }

    // -------------------------------------------------------------------------------------------------
    // a grammar we were not allowed to read
    // -------------------------------------------------------------------------------------------------

    /**
     * The hardening against XXE neutralizes every external DTD, so a document naming one declares a grammar
     * that never arrives and every element in it is undeclared as far as the parser can see. A document
     * cannot be held to a rule we refused to read.
     */
    @Test
    public void aDocumentNamingAnExternalGrammarIsReadRatherThanRefused() throws IOException {
        Properties props = read("<!DOCTYPE config SYSTEM \"http://example.com/config.dtd\">"
                + "<config><host>alpha</host><port>8080</port></config>");

        assertEquals("alpha", props.getProperty("config.host"));
        assertEquals("8080", props.getProperty("config.port"));
    }

    // -------------------------------------------------------------------------------------------------
    // the way out
    // -------------------------------------------------------------------------------------------------

    @Test
    public void validateFalseReadsADocumentWhateverItsGrammarSaysOfIt() throws IOException {
        Properties props = read(OWN_DTD + "<config><host>alpha</host><port>8080</port></config>",
                "#validate=false");

        assertEquals("alpha", props.getProperty("config.host"));
        assertEquals("8080", props.getProperty("config.port"));
    }

    /** The same escape hatch, and the same words, for the format the JDK defines. */
    @Test
    public void validateFalseAlsoReadsAPropertiesDocumentThatBreaksItsDtd() throws IOException {
        Properties props = read(
                JAVA_DTD + "<properties><entry key=\"host\">alpha</entry><bogus/></properties>",
                "#validate=false");

        assertEquals("the format is still recognised by its DOCTYPE", "alpha", props.getProperty("host"));
        assertNull("and it is not read as an ordinary document", props.getProperty("properties.entry"));
    }

    @Test
    public void validateTrueIsWhatHappensAnyway() throws IOException {
        Properties props = read(OWN_DTD + "<config><host>alpha</host></config>", "#validate=true");

        assertEquals("alpha", props.getProperty("config.host"));
    }

    @Test
    public void anythingElseThanTheTwoWordsIsRefused() {
        try {
            read(OWN_DTD + "<config><host>alpha</host></config>", "#validate=maybe");
            fail("a setting that is neither true nor false was accepted");
        } catch (IOException e) {
            fail("expected the option to be refused before reading: " + e);
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("validate"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("maybe"));
        }
    }
}
