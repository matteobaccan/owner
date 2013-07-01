---
layout: docs
title: Basic Usage
prev_section: home
next_section: structure
permalink: /docs/usage/
---

The approach used by OWNER APIs, is to define a Java interface associated to a
properties file.

Suppose your properties file is defined as `ServerConfig.properties`:

{% highlight properties %}
port=80
hostname=foobar.com
maxThreads=100
{% endhighlight %}

To access this property you need to define a convenient Java interface in
`ServerConfig.java`:

{% highlight java %}
public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
{% endhighlight %}

We'll call this interface the *Properties Mapping Interface* or just
*Mapping Interface* since its goal is to map Properties into a an easy to use
piece of code.

<div class="note">
  <h5>How does the mapping work?</h5>
  <p>
Since the properties file does have the same name as the Java class, and they
are located in the same package, the OWNER API will be able to automatically
associate the properties between the properties file and the Java class.
So the property <tt>port</tt> defined in the properties file will be associated to the
method <tt>int port()</tt> in the Java class.
  </p>
<p>
The mapping mechanism is fully customizable, later we will see how to change it.
</p>
</div>

Then, you can use it from inside your code:

{% highlight java %}
ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
System.out.println("Server " + cfg.hostname() + ":" + cfg.port() +
                   " will run " + cfg.maxThreads());
{% endhighlight %}

Did you notice that there is also the `@DefaultValue("42")` annotation specified
in the example? It is used in case the `maxThread` key is missing from the
properties file.
This annotation gets automatically converted to `int`, since `maxThreads()`
returns an `int`. See next chapters to learn more about automatic type
conversion.

Using the annotations, you can also customize the property keys:

{% highlight properties %}
# Example of property file 'ServerConfig.properties'
server.http.port=80
server.host.name=foobar.com
server.max.threads=100
{% endhighlight %}

This time, as commonly happens in Java applications, the properties names are
separated by dots. Instead of just "port" we have "server.http.port", so we
need to map this property name to the associated method using the `@Key`
annotation.

{% highlight java %}
/*
 * Example of ServerConfig.java interface mapping the previous properties file
 */
public interface ServerConfig extends Config {
    @Key("server.http.port")
    int port();

    @Key("server.host.name")
    String hostname();

    @Key("server.max.threads");
    @DefaultValue("42")
    int maxThreads();
}
{% endhighlight %}

The `@DefaultValue` and `@Key` annotations are the basics to start using the
OWNER API.

<div class="note">
  <h5>You can leave the properties file away during development!</h5>
  <p>
     During the development you may decide to just use the `@DefaultValue` to
     provide a default configuration, without really adding the properties file.
     You can add the properties file later or leave this task to the end user.
  </p>
</div>