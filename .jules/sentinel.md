## 2026-10-07 - Constant-time Array Comparison Timing Side-Channel
**Vulnerability:** `EncryptTool.constantTimeEquals` returned immediately when `a.length != b.length`, exposing array length through execution timing differences.
**Learning:** Early returns in helper functions designed for constant-time comparisons introduce subtle timing side-channels on input length even when element comparisons are loop-bitwise-ORed.
**Prevention:** Bitwise-OR `a.length ^ b.length` into the accumulator and iterate through elements without short-circuiting when array lengths differ.
