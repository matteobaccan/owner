## 2026-09-18 - Scope URI credential hiding to authority section
**Vulnerability:** `Util.hideCredentials` used `indexOf('@', host)` across the full URI string, leaking partial password data when passwords contained `@` and corrupting non-credential URIs containing `@` in path/query/fragment.
**Learning:** Naive URI string matching with `indexOf('@')` risks leaking credentials or misinterpreting path/query components.
**Prevention:** Bound searching for userinfo `@` separators strictly within the URI authority section before the first path, query, or fragment delimiter (`/`, `?`, `#`).
