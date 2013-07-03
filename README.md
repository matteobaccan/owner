OWNER
=====

OWNER, an API to ease Java property files usage.

Jenkins: [![Build Status](https://aeonbits.ci.cloudbees.com/job/owner-api/badge/icon)](https://aeonbits.ci.cloudbees.com/job/owner-api/)  
Travis:  [![Build Status](https://travis-ci.org/lviggiano/owner.png?branch=master)](https://travis-ci.org/lviggiano/owner)

[![Built with Maven](http://maven.apache.org/images/logos/maven-feather.png)](http://owner.newinstance.it/maven-site/)
<span class="border: 1px solid black">
  <a href="http://sheldon.dyndns.tv:9000/images/sonar.png" alt="Powered by Sonar">
    <img src="http://sheldon.dyndns.tv:9000/images/sonar.png">
  </a>
</span>

[![Powered by Sonar](http://sheldon.dyndns.tv:9000/images/sonar.png)](http://sheldon.dyndns.tv:9000/dashboard/index/1)

INTRODUCTION
------------

The goal of OWNER API is to minimize the code required to handle 
application configuration through Java properties files.

BASIC USAGE
-----

The approach used by OWNER APIs, is to define a Java interface 
associated to a properties file.

Suppose your properties file is defined 
as `ServerConfig.properties`:

```properties
port=80
hostname=foobar.com
maxThreads=100
```
    
To access this property you need to define a convenient Java 
interface in `ServerConfig.java`:

```java
public interface ServerConfig extends Config {
    int port();
    String hostname();
    int maxThreads();
}
```

We'll call this interface the *Properties Mapping Interface* or 
just *Mapping Interface* since its goal is to map Properties into 
a an easy to use piece of code.
    
Then, you can use it from inside your code:

```java
public class MyApp {    
    public static void main(String[] args) {
        ServerConfig cfg = ConfigFactory.create(ServerConfig.class);
        System.out.println("Server " + cfg.hostname() + ":" + cfg.port() + " will run " + cfg.maxThreads());
    }
}
```

But this is just the tip of the iceberg. 

Continue reading here: [Basic usage](http://owner.aeonbits.org/docs/usage/).

DOCUMENTATION 
-------------

Make sure to have a look at the [documentation website][website] 
to learn how flexible and powerful OWNER is, and why you may need it!

  [website]: http://owner.aeonbits.org


LICENSE
-------

OWNER is released under the BSD license.  
See [LICENSE][] file included for the details.  

  [LICENSE]: https://raw.github.com/lviggiano/owner/master/LICENSE
