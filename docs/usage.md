---
layout: docs
title: Basic Usage
prev_section: installation
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

To access this properties file you need to define a convenient Java interface 
`ServerConfig.java` in the same package:

{% highlight java %}
import org.aeonbits.owner.Config;

public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")
    int maxThreads();
}
{% endhighlight %}

Notice that the above interface extends from `Config`, that
is a marker interface recognized by OWNER as valid to work with.

We'll call this interface the *Properties Mapping Interface* or just
*Mapping Interface* since its goal is to map Properties into a an easy to use
piece of code.


How does the mapping work?
--------------------------

Since the properties file does have the same name as the Java class, and they
are located in the same package, the OWNER API will be able to automatically
associate them.  
For instance, if your *mapping interface* is called `com.foo.bar.ServerConfig`, 
OWNER will try to associate it to `com.foo.bar.ServerConfig.properties`, 
loading from the classpath.  


The properties names defined in the properties file will be associated to the
methods in the Java class having the same name.  
For instance, the property `port` defined in the properties file will be 
associated to the method `int port()` in the Java class, the property `hostname`
will be associated to the method `String hostname()` and the appropriate type
conversion will apply automatically, so the method `port()` will return an int
while the method `hostname()` will return a Java string, since the interface is
defined in this way.

The mapping mechanism is fully customizable, as well the automatic type 
conversion we just introduced is flexible enough to cover most of the Java types 
as well as object types defined by the user.  
You can see how in the next chapters.

## Using the Config object

At this point, you can create the ServerConfig object and use it in your code:

{% highlight java %}
ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
System.out.println("Server " + cfg.hostname() + ":" + cfg.port() +
                   " will run " + cfg.maxThreads());
{% endhighlight %}


Using @DefaultValue and @Key annotations
----------------------------------------

Did you notice that in the above example it is specified `@DefaultValue("42")` 
annotation? 

{% highlight java %}
public interface ServerConfig extends Config {
    int port();
    String hostname();
    @DefaultValue("42")    // here!!!
    int maxThreads();
}
{% endhighlight %}

It is used in case the `maxThread` key is missing from the
properties file.

This annotation gets automatically converted to `int`, since `maxThreads()`
returns an `int`. 

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
 * Example of ServerConfig.java interface mapping the previous 
 * properties file.
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


Conclusions
-----------

Now you know the minimum to get productive with the OWNER API. But this is just
the beginning. OWNER is a rich API that allows you to add additional behaviors
and have more interesting features, so that you should be able to use this 
library virtually in any other context where you where using the 
`java.util.Properties` class.
