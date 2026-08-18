---
title: "Singleton"
---

It makes sense for the configuration for an application to be a sort of [Singleton](http://c2.com/cgi/wiki?SingletonPattern).

In the most common case, to instantiate a `Config` object, you would do:

```java
MyConfig cfg = ConfigFactory.create(MyConfig.class);
```

People tends to use this as:

```java

class MyApp {
    private static MyConfig cfg =
        ConfigFactory.create(MyConfig.class);

    public void doSomething() {
        UserInterface ui = new UserInterface(cfg);
        Model model = new Model(cfg);
        ui.setModel(model);
        // do something more with cfg...
    }
}

```

The problem is that, it may be not very practical to pass the `cfg` object inside complex applications,
and if you use the `ConfigFactory.create()` in multiple places you'll end up in having multiple instances of the
`cfg` objects. And this may not be what you need.

For instance, if you have a J2EE Web application, to have a config object inside your servlets,  you should configure a
`ServletContextListener` in your `web.xml` and bind the configuration object to the `ServletContext`.

Then retrieve the `cfg` object in your servlets in the `init()` method.

Example:

```java
public class MyServletContextListener
    implements ServletContextListener {

    public void contextInitialized(ServletContextEvent sce) {
        MyConfig cfg = ConfigFactory.create(MyConfig.class);
        sce.getServletContext()
            .setAttribute("com.acme.foo.bar.MyConfig", cfg);
    }

    public void contextDestroyed(ServletContextEvent sce) {
        sce.getServletContext()
            .removeAttribute("com.acme.foo.bar.MyConfig");
    }
}

// then in your servlets

public class MyServlet extends HttpServlet {
    private MyConfig cfg = null;

    public void init(ServletConfig config)
              throws ServletException {
        cfg = (MyConfig)config.getServletContext()
            .getAttribute("com.acme.foo.bar.MyConfig");
    }

    public void destroy() {
        cfg = null;
    }

    protected void doGet(HttpServletRequest req,
                        HttpServletResponse resp)
        throws ServletException, IOException {

        // do something with cfg;

    }
}
```

I don't dislike the above example, since it's a kind of
[**dependency injection**](http://c2.com/cgi/wiki?DependencyInjection) mechanism (you don't necessarily need a
framework to do [IoC](http://c2.com/cgi/wiki?InversionOfControl)) and I usually prefer this over Singletons
(See [SingletonsAreEvil](http://c2.com/cgi/wiki?SingletonsAreEvil)), but... this is still pretty much of code.
Boilerplate code. The kind of code OWNER is supposed to remove.

So, this is why a sort of Singleton has been provided: I called it `ConfigCache`, since it's a little bit more
than a mere (evil) Singleton. And this makes me feel a bit better about it :-)


The ConfigCache
---------------


So, as we've seen many times before, you should be familiar with the `ConfigFactory`:

```java
MyConfig instance = ConfigFactory.create(MyConfig.class);
```

The same way you can use the new `ConfigCache`:

```java
MyConfig instance = ConfigCache.getOrCreate(MyConfig.class);
```

The difference is that, when using `ConfigFactory` a new instance of the `MyConfig` object is created every time,
instead when using the `ConfigCache`, instances are returned from an internal cache.

```java
MyConfig firstFromFactory = ConfigFactory.create(MyConfig.class);
MyConfig secondFromFactory = ConfigFactory.create(MyConfig.class);
// firstFromFactory not same as secondFromFactory

MyConfig firstFromCache = ConfigCache.getOrCreate(MyConfig.class);
MyConfig secondFromCache = ConfigCache.getOrCreate(MyConfig.class);
// firstFromCache same as secondFromCache
```

You can assign an `id` to an instance:

```java
MyConfig firstFromCache = ConfigCache.getOrCreate("foo", MyConfig.class);
MyConfig secondFromCache = ConfigCache.getOrCreate("foo", MyConfig.class);
MyConfig thirdFromCache = ConfigCache.getOrCreate("bar", MyConfig.class);
// firstFromCache same as secondFromCache
// thirdFromCache not same as secodFromCache or firstFromCache
```

The `id` is defined as `java.lang.Object`, but you can use a `String` such as a name, as in the above example.

A `String` constant declared on the mapping interface itself makes a good id, and it is what
[#148](https://github.com/matteobaccan/owner/issues/148) was asking for — fetching a configuration by
something it declares, rather than by its class:

```java
public interface SampleConfiguration extends Config {
    String PORT = "abc.port";

    @Key(PORT)
    int port();
}
```

```java
ConfigCache.getOrCreate(SampleConfiguration.PORT, SampleConfiguration.class);   // once, at startup
…
SampleConfiguration config = ConfigCache.get(SampleConfiguration.PORT);         // anywhere else
```

<div class="note info">
  <h5>Why the id is chosen and not deduced.</h5>
  <p>
    It would be tempting to skip the first line and let the cache find "the configuration that declares
    this property". It cannot: <b>a property name is not an identity</b>. Two mapping interfaces reading one
    file may perfectly well declare the same key — that is the ordinary way of splitting a large
    configuration into the parts each module cares about — so the question would have two answers and the
    library would be picking one of them for you.
  </p>
</div>

In some cases, it may be useful list all configuration objects in an application, for instance for debugging. This can
be accomplished using the `ConfigCache.list()` method, which returns a set of the `id` objects in the cache. This set
can be used to iterate over all configuration objects in the cache, for instance as follows.

```java
for (Object id : ConfigCache.list()) {
   Config cfg = ConfigCache.get(id);
   // do something
}
```

As for the `ConfigFactory` you can pass a list of imports to `ConfigCache`. In fact the `ConfigCache` interface is
pretty similar to `ConfigFactory`:

```java
public final class org.aeonbits.owner.ConfigCache {
  public static <T extends Config> T getOrCreate(Class<? extends T> clazz, Map<?, ?>... imports);
  public static <T extends Config> T getOrCreate(Factory factory, Class<? extends T> clazz, Map<?, ?>... imports);
  public static <T extends Config> T getOrCreate(Object id, Class<? extends T> clazz, Map<?, ?>... imports);
  public static <T extends Config> T getOrCreate(Factory factory, Object id, Class<? extends T> clazz, Map<?, ?>... imports);
  public static <T extends Config> T get(Object id);
  public static <T extends Config> T add(Object id, T config);
  public static void clear();
  public static <T extends Config> T remove(Object id);
}
```

The ConfigCache is designed to be thread safe, so you don't have to worry about concurrent access.

One instance per thread
-----------------------

**Thread safe is not the same as thread isolated**, and the difference is the whole of
[#283](https://github.com/matteobaccan/owner/issues/283): several threads may ask the cache for a
configuration at the same time without corrupting it, but `getOrCreate(MyConfig.class)` uses the class as
the id, so what they all get is *one* object. If that object is `Mutable`, what one thread writes the next
one reads.

Wrapping it in a `ThreadLocal` does not change that: the supplier returns the shared instance, so every
thread caches the same object.

```java
// still one single instance for the whole JVM
ThreadLocal<MyConfig> config =
    ThreadLocal.withInitial(() -> ConfigCache.getOrCreate(MyConfig.class));
```

An instance per thread needs no new API — the id is any object, so make the thread the id:

```java
MyConfig config = ConfigCache.getOrCreate(Thread.currentThread().getName(), MyConfig.class);
```

<div class="note warning">
  <h5>The cache does not forget.</h5>
  <p>
    An id per thread leaves an instance per thread in the cache, and it stays there after the thread is
    gone: nothing evicts it but <code>ConfigCache.remove(id)</code>. That is fine for a fixed set of
    long-lived workers, and it is a leak for threads that come and go — a request per thread, a pool that
    renames its threads. For those, put the <code>ThreadLocal</code> over the <b>factory</b> instead of over
    the cache: same isolation, and the instance is collected with the thread that owned it.
  </p>
</div>

```java
private static final ThreadLocal<MyConfig> CONFIG =
    ThreadLocal.withInitial(() -> ConfigFactory.create(MyConfig.class));
```

Note the `static final`: a `ThreadLocal` built inside the method that uses it is a new `ThreadLocal` on
every call, which caches nothing at all.

Since a cached instance is the one created the first time, it also keeps the
settings of the factory that created it. That matters for the
[prefix configured on a factory](/owner/docs/key-prefix/), which is
read when the Config object is created: asking `ConfigCache` for the same `id`
again returns the existing object, prefix included, whatever the factory
handed over the second time says.
