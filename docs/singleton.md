---
layout: docs
title: Singleton
prev_section: event-support
next_section: crypto
permalink: /docs/singleton/
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
