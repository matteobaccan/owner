## 2026-10-19 - Temporary File Creation Permissions (`File` vs `Files`)
**Vulnerability:** Use of `File.createTempFile` creates temporary files with default POSIX permissions (world-readable/writable in system shared temporary directories like `/tmp`), potentially exposing temporary file contents to other local users.
**Learning:** `java.io.File.createTempFile` defaults to standard platform permissions, whereas `java.nio.file.Files.createTempFile` sets strict owner-only permissions (e.g., `0600` on POSIX systems) by default when creating files in shared temporary directories.
**Prevention:** Always prefer `java.nio.file.Files.createTempFile` (or `Files.createTempDirectory`) over `java.io.File.createTempFile` for secure temporary file handling.
