# Sentinel Security Journal

## 2026-10-09 - Prefer `Files.createTempFile` Over `File.createTempFile` for Secure Permissions
**Vulnerability:** `File.createTempFile` creates temporary files with default world-readable permissions (e.g. `0644` or `0666`), potentially exposing sensitive configuration or data to other local users on shared systems.
**Learning:** In Java NIO, `Files.createTempFile` sets POSIX owner-only access permissions (`0600`) by default on supporting filesystems.
**Prevention:** Always prefer `java.nio.file.Files.createTempFile` over `java.io.File.createTempFile` when creating temporary files or buffers.
