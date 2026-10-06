## 2026-10-06 - Zero sensitive plaintext byte arrays after decryption
**Vulnerability:** Decrypted plaintext byte arrays (`byte[] plain`) remained on the heap until garbage collection after `new String(plain, StandardCharsets.UTF_8)` was called in cryptographic handlers (`AesGcmHandler` and `RsaHandler`).
**Learning:** Returning `new String(plain, ...)` directly inside `resolve()` leaves raw secret bytes in heap memory where heap dumps or memory inspection could disclose them.
**Prevention:** Assign `cipher.doFinal(...)` to a local `byte[] plain` variable and wrap `new String(plain, ...)` in a `try ... finally` block that calls `Arrays.fill(plain, (byte) 0)` to clear sensitive plaintext data immediately after conversion.
