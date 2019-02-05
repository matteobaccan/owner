---
layout: docs
title: Frequently Asked Questions
prev_section: why
next_section: support
permalink: /docs/faq/
---

## What does "OWNER" name mean?

Since this API is used to access *Properties* files, and we implement mapping interfaces to deal with those,
somehow interfaces are *owners* for the properties. So here comes the name OWNER, all uppercase because when you speak
about it you must shout it out :-)

The true story, is that I tried to find a decent name for the project, but I didn't come out with anything better.
Sorry.

## Is OWNER a stable API?

The codebase is very compact, and I try to keep the test coverage to 100%, developing many tests for each new feature.
You have the source, you can help improving the library and fix the bugs if you find some.

Still, OWNER API is a very early project, and APIs may change in the future to add/change some behaviors. But the
philosophy is to keep always backward compatibility (unless not possible).

## What happens if some property is not set to any value?

See: [Undefined properties]({{ site.url }}/docs/usage/#toc_3) in [Basic usage]({{ site.url }}/docs/usage/) chapter.

## How about the security of storing password in properties? Does OWNER support encryptable properties like in [Jasypt](http://www.jasypt.org/encrypting-configuration.html) ?

At the current stage (version 1.0.5) OWNER doesn't allow you to specify that a configuration property is encrypted,
but OWNER APIs are flexible enough to let the user implement that. An example is [here][enc-props].

Encrypted properties are in the list of goals for the next versions (See [#49](https://github.com/lviggiano/owner/issues/49)).

  [enc-props]: https://github.com/lviggiano/owner/blob/master/src/test/java/org/aeonbits/owner/examples/EncryptedPropertiesExample.java

## Why OWNER API doesn't implement this ${pretty.neat.feature} ?

Explain it on [GitHub issues][issues]. If I like the idea I will implement it.
Or, you can implement by yourself and send me a push request on GitHub.
See also [Contributing]({{ site.url }}/docs/contributing/).

The idea is to keep things minimal and code clean and easy. And for every new feature, having a complete test suite to
verify all cases.

  [properties]: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html
  [issues]: https://github.com/lviggiano/owner/issues

## Who is using OWNER?

I have no idea. I implemented OWNER for a web application I was working on, then I decided to share it. And it looks
like [somebody][#32] is now using it. Including my friend [Bruno] who is a great guy and makes me proud of it.

If you like us to know you are using OWNER, drop a comment [here][#32].
Or maybe you just want to go [here](https://www.openhub.net/p/owner/users) and click on "I use this!".

<span style="bgcolor:white">
<script type='text/javascript' src='https://www.openhub.net/p/owner/widgets/project_factoids?format=js'></script>
</span>

The fact I am receiving feedback, request for features and changes, mails and questions, makes me think that this
library is useful to some people around the world, and this encourages me to work and improve it.
So, don't be shy to introduce yourself.

  [#32]: https://github.com/lviggiano/owner/issues/32
  [Bruno]: https://github.com/lviggiano/owner/issues/32#issuecomment-19466459
