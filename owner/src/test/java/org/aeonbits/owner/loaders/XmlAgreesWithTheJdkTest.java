/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.loaders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * {@link XMLLoader} against {@link Properties#loadFromXML(java.io.InputStream)}, on the format they both
 * read.
 * <p>
 * The Java XML properties format has a <b>reference implementation, and it is in the JDK</b>. That makes
 * this the cheapest conformance suite there is: the same document goes through both, and where they
 * disagree one of them is wrong — and it is not the one in the JDK. It is the same idea as holding the
 * TOML parser to <code>toml-test</code>, with the corpus written here because the format is small enough
 * to enumerate.
 * </p>
 * <p>
 * <b>It found two.</b> An entry holding <code>"  spaced  "</code> came back trimmed, and an entry that was
 * empty came back as no key at all — which in this library is a different thing from an empty value, since
 * only an absent key lets a {@code @DefaultValue} win. Both came from the other half of this loader, the
 * one that reads an XML of your own, where trimming is right because the whitespace is a pretty-printer's
 * and an empty element is a container rather than a value.
 * </p>
 *
 * @author Matteo Baccan
 */
public class XmlAgreesWithTheJdkTest {

    private static final String DTD =
            "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">";

    private static final File DIR = new File("target/xml-vs-jdk");

    private int documents;

    @Before
    public void before() {
        DIR.mkdirs();
    }

    @After
    public void after() {
        File[] found = DIR.listFiles();
        if (found != null)
            for (File file : found)
                file.delete();
    }

    /**
     * Reads the same document both ways and asserts they agree, which is the whole of every test below.
     *
     * @param body what goes inside {@code <properties>}.
     */
    private void bothAgreeOn(String body) throws IOException {
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + DTD
                + "<properties>" + body + "</properties>";

        Properties fromTheJdk = new Properties();
        fromTheJdk.loadFromXML(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));

        File file = new File(DIR, "d" + documents++ + ".xml");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(document.getBytes(StandardCharsets.UTF_8));
        }
        Properties fromUs = new Properties();
        new XMLLoader().load(fromUs, file.toURI());

        assertEquals(body, new TreeMap<Object, Object>(fromTheJdk).toString(),
                new TreeMap<Object, Object>(fromUs).toString());
    }

    @Test
    public void anOrdinaryEntry() throws IOException {
        bothAgreeOn("<entry key=\"a\">1</entry>");
    }

    /**
     * An entry with nothing in it. The JDK reads the empty value; this library distinguishes a key that is
     * present and empty from one that is absent, and only the second lets a default win — so dropping it
     * changed which value the configuration answered with.
     */
    @Test
    public void anEntryWithAnEmptyValue() throws IOException {
        bothAgreeOn("<entry key=\"a\"></entry>");
    }

    /**
     * And the whitespace inside one, which the JDK keeps. A value that is spaces on purpose — a separator,
     * a padded field — survives the round trip only if nobody trims it on the way in.
     */
    @Test
    public void anEntryWhoseValueIsPaddedWithSpaces() throws IOException {
        bothAgreeOn("<entry key=\"a\">  spaced  </entry>");
    }

    /** The comment element, which belongs to the format and is not a property. */
    @Test
    public void theCommentElementIsNotAProperty() throws IOException {
        bothAgreeOn("<comment>hello</comment><entry key=\"a\">1</entry>");
    }

    /** A name written twice: the last one wins, in both. */
    @Test
    public void aNameWrittenTwice() throws IOException {
        bothAgreeOn("<entry key=\"a\">1</entry><entry key=\"a\">2</entry>");
    }

    /** The empty name, which the format allows and neither of the two refuses. */
    @Test
    public void theEmptyName() throws IOException {
        bothAgreeOn("<entry key=\"\">1</entry>");
    }

    /** Entities, character data and a numeric reference: XML's own escaping, read by the parser. */
    @Test
    public void everyWayOfEscapingAValue() throws IOException {
        bothAgreeOn("<entry key=\"a\">&lt;&amp;&gt;</entry>");
        bothAgreeOn("<entry key=\"a\"><![CDATA[<raw>]]></entry>");
        bothAgreeOn("<entry key=\"a\">&#128640;</entry>");
    }

    /** A value on more than one line, where the break belongs to the value. */
    @Test
    public void aValueThatSpansLines() throws IOException {
        bothAgreeOn("<entry key=\"a\">one\ntwo</entry>");
    }

    /** A name and a value outside ASCII, the document having said it is UTF-8. */
    @Test
    public void aNameAndAValueOutsideAscii() throws IOException {
        bothAgreeOn("<entry key=\"caffè\">设置</entry>");
    }

    /** A document with no entries at all, which is a configuration with no properties and not an error. */
    @Test
    public void aDocumentWithNoEntries() throws IOException {
        bothAgreeOn("");
    }
}
