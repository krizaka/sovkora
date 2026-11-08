---
title: Asset Encryption Format
description: The on-disk envelope every asset is written in, and the two implementations that have to agree on it.
category: Core
order: 8
---

# Asset encryption — the envelope format

The asset store is written by **three processes in two languages**: the conversation service
(uploads and serving), the job service (step inputs and outputs), and the Python media worker. This
is the format all three speak. It has two implementations —
`engine/sovkora-engine-assets/…/EnvelopeCodec.java` and
`workers/media/app/envelope.py` — and they are pinned to each other by
a fixture test: Java writes `target/interop/java-written.bin`, Python opens it and writes its own,
and Java opens that. Neither side can drift without the other going red.

## Layout

```
offset  size  field
     0     8  magic            "ORZAENC1"
     8     1  version          0x01
     9     1  algorithm        0x01 = AES-256-GCM, one sealed message per block
    10     4  blockSize        big-endian uint32, plaintext bytes per block
    14     8  plainLength      big-endian uint64
    22     2  keyIdLen         big-endian uint16
    24     N  keyId            UTF-8
  24+N     2  wrappedLen       big-endian uint16
  26+N     M  wrapped          the wrapped data key — OPAQUE to this format
26+N+M     8  noncePrefix      random, per file
                               headerLength = 34 + N + M

then ceil(plainLength / blockSize) blocks, each:
    ciphertext (blockSize, or the remainder for the last) || GCM tag (16 bytes)
    nonce = noncePrefix (8) || blockIndex (4, big-endian)
    AAD   = the whole header || blockIndex (4, big-endian)
```

## Why it is shaped this way

**Envelope, not direct encryption.** Each file carries its own data key, wrapped under a master key
the format never sees. Rotating the master key is a line in the keyring — new files use the new
key, every old file still names the one that wrapped it and still opens. Encrypting the bytes
directly under a master key would make rotation a rewrite of the whole store, which is the first
thing a compliance audit asks about.

**`wrapped` is opaque.** The writer stores whatever the key provider returned and the reader hands
it back unexamined. A file-backed provider puts a nonce and a GCM tag in there; a KMS puts its own
blob. That is what lets a KMS replace the local provider without touching the format, the writers,
the readers or the migrator.

**Blocks, because of `Range`.** AES-GCM's tag covers a whole message, so a file sealed as one
message cannot be decrypted from an arbitrary offset — serving the last second of a video would
mean decrypting the video. Sealing independent blocks makes a seek land on a block boundary and
decrypt one block. `BlockDecryptingInputStream` implements `skip()` as a seek, which is how Spring
serves a `Range`, and 64 KiB is the default so a range over-reads at most that much at each end.

**The AAD is the whole header.** Not a field of it. Truncating the file, rewriting its recorded
length, swapping two blocks, or grafting a block from another file all fail at the tag rather than
decrypting to something. The block index is appended so blocks cannot be reordered.

**Nonces cannot repeat.** The data key is unique per file, so the counter only has to be unique
within one file; the random 8-byte prefix means two files never share a nonce space even if a key
were somehow reused.

## The keyring

Outside the repository, outside `.env`, mode `0600`:

```
# orazaka master keyring v1
active = k2
k1 = <base64 of 32 bytes>
k2 = <base64 of 32 bytes>
```

`active` is the key new files are wrapped under. **Every retired key must stay in the file for as
long as one asset still names it** — a keyring that drops `k1` cannot open anything written under
it, and says so rather than degrading to plaintext.

## Operations

```bash
CP="engine/sovkora-engine-assets/target/classes:$(ls ~/.m2/repository/org/slf4j/slf4j-api/*/slf4j-api-*.jar | tail -1)"
TOOL=com.krizaka.orazaka.assets.infrastructure.tool.AssetEncryptionTool

java -cp "$CP" $TOOL keygen  ~/.orazaka/master.key k1              # create, or rotate: keygen … k2
java -cp "$CP" $TOOL migrate ~/.orazaka/master.key var/orazaka-uploads
java -cp "$CP" $TOOL verify  ~/.orazaka/master.key var/orazaka-uploads
```

`migrate` is **resumable because it holds no state**: a file is converted if and only if it opens
with the magic, so "where did it stop" is a question the store answers itself. Each file is written
to a sibling temp and moved into place, so none is ever half converted. `verify` opens every file
and checks it yields the length its header claims — the difference between "the migration ran" and
"the migration worked".

## Configuration

| Key | Default | |
|:---|:---|:---|
| `orazaka.assets.encryption.enabled` | `true` | off means new writes are in the clear; a migration state, and a loud one |
| `orazaka.assets.encryption.master-key-file` | `${user.home}/.orazaka/master.key` | never `.env` |
| `orazaka.assets.encryption.block-size` | `65536` | the granularity of a `Range` read |
| `orazaka.assets.encryption.accept-plaintext` | `false` | **migration only.** A permanent tolerance is a permanent door |

The Python worker reads `ORAZAKA_ASSETS_MASTER_KEY_FILE` and `ORAZAKA_ASSETS_ENCRYPTION_ENABLED`.
