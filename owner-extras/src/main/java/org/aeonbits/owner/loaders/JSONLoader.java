/*
 * Copyright 2015 ThoughtWorks, Inc.
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.loaders;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;
import java.util.Stack;

/**
 * A {@link Loader loader} that can read properties from a JSON file.
 *
 * @author Ketan Padegaonkar
 * @since 1.0.10
 */
public class JSONLoader implements Loader {

    public boolean accept(URI uri) {
        try {
            URL url = uri.toURL();
            return url.getFile().toLowerCase().endsWith(".json");
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    public void load(Properties result, URI uri) throws IOException {
        URL url = uri.toURL();
        InputStream input = url.openStream();
        try {
            load(result, input);
        } finally {
            input.close();
        }
    }

    void load(Properties result, InputStream input) throws IOException {
        new JsonToPropsHandler(result).parse(input);
    }

    public String defaultSpecFor(String urlPrefix) {
        return urlPrefix + ".json";
    }

    class JsonToPropsHandler {
        private final Properties props;
        private final Stack<String> paths = new Stack<String>();
        private final Stack<StringBuilder> value = new Stack<StringBuilder>();

        JsonToPropsHandler(Properties props) {
            this.props = props;
        }

        public void parse(InputStream input) throws IOException {
            parse(new JsonReader(new InputStreamReader(input, "UTF-8")));
        }

        private void parse(JsonReader reader) throws IOException {
            while (reader.hasNext()) {
                JsonToken token = reader.peek();
                switch (token) {
                    case BEGIN_OBJECT:
                        reader.beginObject();
                        parse(reader);
                        reader.endObject();
                        if (!value.isEmpty())
                            value.pop();
                        if (!paths.isEmpty())
                            paths.pop();
                        break;
                    case BEGIN_ARRAY:
                        value.push(new StringBuilder());
                        reader.beginArray();

                        while (reader.hasNext()) {
                            JsonToken arrayElementToken = reader.peek();
                            switch (arrayElementToken) {
                                case STRING:
                                    value.peek().append(reader.nextString());
                                    break;
                                case NUMBER:
                                    value.peek().append(reader.nextInt());
                                    break;
                                case BOOLEAN:
                                    value.peek().append(reader.nextBoolean());
                                    break;
                                case NULL:
                                    reader.nextNull();
                                    value.peek().append((String) null);
                            }

                            if (reader.hasNext()) {
                                value.peek().append(", ");
                            }
                        }
                        reader.endArray();
                        endObject();
                        break;
                    case NAME:
                        String name = reader.nextName();
                        String path = (paths.size() == 0) ? name : paths.peek() + "." + name;
                        paths.push(path);
                        break;
                    case STRING:
                        value.push(new StringBuilder());
                        value.peek().append(reader.nextString());
                        endObject();
                        break;
                    case NUMBER:
                        value.push(new StringBuilder());
                        value.peek().append(reader.nextInt());
                        endObject();
                        break;
                    case BOOLEAN:
                        value.push(new StringBuilder());
                        value.peek().append(reader.nextBoolean());
                        endObject();
                        break;
                    case NULL:
                        value.push(new StringBuilder());
                        reader.nextNull();
                        value.peek().append((String) null);
                        endObject();
                        break;
                    case END_DOCUMENT:
                        reader.close();
                        return;
                    default:
                        break;
                }
            }
        }

        private void endObject() {
            String key = paths.peek();
            String propertyValue = this.value.peek().toString().trim();
            if (!propertyValue.isEmpty())
                props.setProperty(key, propertyValue);
            value.pop();
            paths.pop();
        }
    }
}
