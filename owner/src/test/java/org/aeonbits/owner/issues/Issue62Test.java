/*
 * Copyright (c) 2012-2026, Luigi R. Viggiano, Matteo Baccan
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */
package org.aeonbits.owner.issues;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Mutable;
import java.beans.PropertyChangeListener;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * See: https://github.com/matteobaccan/owner/issues/62
 * <p>
 * lviggiano asked in 2013 for a <code>loadFromXML()</code> on {@link Mutable} that would be <b>more than a
 * delegate</b> to {@link java.util.Properties#loadFromXML(InputStream)}: one that reads the document the
 * way this library reads an XML source, so that user-defined formats work. It never got written, and the
 * asymmetry it left is visible from the other side - {@link Accessible#storeToXML} has been there since
 * 1.0.5, with nothing to read back what it writes except <code>java.util.Properties</code>.
 * </p>
 *
 * @author Matteo Baccan
 */
public class Issue62Test {

    public interface Server extends Mutable, Accessible {

        @Config.DefaultValue("localhost")
        String host();

        @Config.Key("server.http.port")
        @Config.DefaultValue("0")
        int httpPort();
    }

    private static InputStream xml(String document) {
        return new ByteArrayInputStream(document.getBytes(UTF_8));
    }

    /** The format {@link java.util.Properties} defines, which is what <code>storeToXML</code> writes. */
    @Test
    public void theJavaPropertiesFormatIsRead() throws IOException {
        Server config = ConfigFactory.create(Server.class);

        config.loadFromXML(xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties><entry key=\"host\">db.internal</entry></properties>"));

        assertEquals("db.internal", config.host());
    }

    /**
     * And the half that made the request worth making: <b>any other XML</b>, flattened into keys the way
     * the XML loader flattens a source. <code>java.util.Properties</code> refuses this document outright.
     */
    @Test
    public void anXmlOfYourOwnIsReadTheWayASourceWouldBe() throws IOException {
        Server config = ConfigFactory.create(Server.class);

        config.loadFromXML(xml("<server><http><port>8080</port></http></server>"));

        assertEquals(8080, config.httpPort());
    }

    /** It is a load like the others: the listeners are told, and what was there already survives. */
    @Test
    public void itIsALoadLikeTheOthers() throws IOException {
        Server config = ConfigFactory.create(Server.class);
        List<String> changed = new ArrayList<>();
        config.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                changed.add(event.getPropertyName());
            }
        });

        config.loadFromXML(xml("<server><http><port>9090</port></http></server>"));

        assertEquals(9090, config.httpPort());
        assertEquals("the default of a key the document does not mention is untouched",
                "localhost", config.host());
        assertTrue(changed.toString(), changed.contains("server.http.port"));
    }

    /** A document that is not well formed is an {@link IOException}, not a configuration read by halves. */
    @Test
    public void aBrokenDocumentIsRefused() {
        Server config = ConfigFactory.create(Server.class);

        try {
            config.loadFromXML(xml("<server><http><port>8080</http></server>"));
            fail("a document that does not parse cannot be half loaded");
        } catch (IOException expected) {
            assertEquals(0, config.httpPort());
        }
    }

    /**
     * <b>The stream is closed</b>, and {@link Mutable#load(InputStream)} leaves its own open. That is not
     * our inconsistency: {@link java.util.Properties#load(InputStream)} leaves the stream open and
     * {@link java.util.Properties#loadFromXML(InputStream)} closes it, measured rather than remembered,
     * and a caller who knows the JDK's pair already knows ours.
     */
    @Test
    public void theStreamIsClosedExactlyAsTheJdkClosesItsOwn() throws IOException {
        final boolean[] closed = {false};
        InputStream stream = new ByteArrayInputStream(
                "<server><http><port>1234</port></http></server>".getBytes(UTF_8)) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };

        ConfigFactory.create(Server.class).loadFromXML(stream);

        assertTrue("closed, as java.util.Properties.loadFromXML closes the stream it is given", closed[0]);
    }
}
