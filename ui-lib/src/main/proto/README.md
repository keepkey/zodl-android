# KeepKey Proto Sources

This directory holds the `.proto` files used to generate the KeepKey message bindings for zodl-android.

## Required files

Copy these three files from `keepkey-firmware/deps/device-protocol/` at firmware commit `de297b7abbbcb0db6fa63ea3865e9926407702c1`:

| File | SHA256 |
|------|--------|
| `messages-zcash.proto` | `4293fe99170b3d9ab17177f80e9e351c6259e06946815b7b26e1d4ebd482286a` |
| `messages.proto` | `e798cec75267dbaa8dd3ee260061f396f19b2090a9832a39e478eb2a21dc6529` |
| `types.proto` | `14e3748fa0f51a3a3df3315714fa13ce4e2d722af09ffef357bf360deb9565ff` |

## Regenerating bindings

After updating any `.proto` file, run:

```bash
./gradlew :ui-lib:generateProto
```

The Gradle protobuf plugin writes generated Kotlin and Java sources to `build/generated/source/proto/` — these are not committed.

## Verifying hashes

The `proto-check` CI workflow automatically verifies hashes on every PR. To check locally:

```bash
sha256sum ui-lib/src/main/proto/messages-zcash.proto \
          ui-lib/src/main/proto/messages.proto \
          ui-lib/src/main/proto/types.proto
```

See `docs/firmware-compat.md` in the integration repo for the update process.
