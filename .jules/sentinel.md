## 2026-10-05 - JNDI Scheme Evasion via URL Prefixes
**Vulnerability:** In `JndiNames.java`, prefixing a remote JNDI URL with `java:` (e.g. `java:ldap://evil.example/dc=x` or `java://evil.example/dc=x`) bypassed the `LOCAL_SCHEME` check because the validation only checked the substring before the first colon.
**Learning:** Scheme validation that checks only `substring(0, colon)` can be bypassed if the input contains nested protocol specifiers (`java:ldap://`) or URL authority delimiters (`://`).
**Prevention:** Check for protocol authority delimiters (`://` and `//`) and ensure scheme comparisons are case-insensitive when validating local vs remote resources.
