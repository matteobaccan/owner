## 2026-09-24 - Zeroing Temporary Raw Key Material in Memory
**Vulnerability:** Temporary byte arrays containing unencrypted raw AES key bytes remained on the heap after key derivation (`AesGcmHandler`) or key wrapping/unwrapping (`RsaHandler`).
**Learning:** `SecretKeySpec` clones the byte array passed to its constructor, leaving the caller's byte array in heap memory until garbage collection.
**Prevention:** Zero out intermediate key material arrays with `Arrays.fill(bytes, (byte) 0)` immediately after passing them to `SecretKeySpec` or cryptographic wrap methods.
