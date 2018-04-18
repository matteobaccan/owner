---
layout: docs
title: XML support
prev_section: configuring
next_section: event-support
permalink: /docs/xml-support/
---

Since JDK 1.5 Java properties support XML format via `loadFromXML()` and `storeToXML()`

There is no reason why OWNER API shouldn't implement XML support as the Properties class.

But OWNER goes beyond that.


The Java XML Properties format
------------------------------

The [`java.util.Properties`](http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html)
class supports an XML format that looks like this:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <comment>this is an example</comment>
    <entry key="server.ssh.alive.interval">60</entry>
    <entry key="server.ssh.address">127.0.0.1</entry>
    <entry key="server.http.port">80</entry>
    <entry key="server.http.hostname">localhost</entry>
    <entry key="server.ssh.user">admin</entry>
    <entry key="server.ssh.port">22</entry>
</properties>
```

Don't you agree that the above example is quite an unstructured XML format?

OWNER is able to load this XML as configuration properties, but it is also possible to specify an XML more structured,
that can be defined from the user on his own taste.


The user's specified XML format
-------------------------------

If you don't like the Java XML Properties format, then you can define an XML of your own:

```xml
<server>
    <http port="80">
        <hostname>localhost</hostname>
    </http>
    <ssh port="22">
        <address>127.0.0.1</address>
        <alive interval="60"/>
        <user>admin</user>
    </ssh>
</server>
```

For OWNER, the above example is equivalent to the example listed in the previous section (the Java XML Properties format)

XML elements, and XML attributes will be converted to properties names, and attribute values and element values will be
converted to properties values.

This means that the above xml would be equivalent to a properties file like the following:

```properties
server.http.port=80
server.http.hostname=localhost
server.ssh.port=22
server.ssh.address=127.0.0.1
server.ssh.alive.interval=60
server.ssh.user=admin
```

Loading the XML
---------------

How to load the XML into OWNER Config object? Simple: in the same way as you already do for properties file.

Suppose you have a *mapping interface* class in a source file called `foo.bar.ServerConfig.java`, just place a
resource in your classpath in `foo.bar.ServerConfig.xml`. Or you can specify the `@Sources` annotation with an URL
pointing to an `file.xml`. OWNER will notice the `.xml` extension and will load the configuration source as XML.

The mapping interface for the two above XML examples is the following:

```java
interface ServerConfig extends Config {

    @Key("server.http.port")
    int httpPort();

    @Key("server.http.hostname")
    String httpHostname();

    @Key("server.ssh.port")
    int sshPort();

    @Key("server.ssh.address")
    String sshAddress();

    @Key("server.ssh.alive.interval")
    int aliveInterval();

    @Key("server.ssh.user")
    String sshUser();
}
```

Storing to XML
--------------

If you are looking for the method to store a Config object into an XML stream, you should have a look at the method
[`storeToXML`][storeToXML] in the `Accessible` interface.

  [storeToXML]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Accessible.html#storeToXML(java.io.OutputStream,+java.lang.String)

When saving a Config object to XML, the Java XML Properties format will be used.

Conclusions
-----------

If you like XML format more than properties format (well, *I don't*), you can use XML to achieve the same result as
using properties files.
