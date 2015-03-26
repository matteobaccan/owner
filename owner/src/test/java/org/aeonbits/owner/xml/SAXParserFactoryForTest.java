/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.xml;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * @author Luigi R. Viggiano
 */
public class SAXParserFactoryForTest extends SAXParserFactory {
    private static SAXParserFactory delegate;

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return delegate.newSAXParser();
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException, 
            SAXNotRecognizedException, SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException, SAXNotRecognizedException, 
            SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    public static void setDelegate(SAXParserFactory delegate) {
        SAXParserFactoryForTest.delegate = delegate;
    }
}
