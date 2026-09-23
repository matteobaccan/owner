## 2026-09-23 - JMX MBean Exposes @Sensitive Properties in Plaintext
**Vulnerability:** Configuration properties annotated with `@Sensitive` were masked in `toString()` and `list()`, but JMX MBean attribute readers (`getAttribute`, `getAttributes`, and `invoke("getProperty", ...)`) returned unmasked plaintext secrets.
**Learning:** When exposing dynamic MBeans or reflection-based administration interfaces (like JMX), ensure sensitive property masking filters apply at the delegate/mbean layer, not just in display/formatting methods.
**Prevention:** Check `PropertiesManager.isSensitiveKey` on property lookup in `JMXSupport` before returning values via JMX.
