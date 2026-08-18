# machikado-java

ED25519 signing for the Machikado Mazoku module ecosystem.
Two-tier: **machikado** (files) + **mazoku** (org auth).
Supports machikado-only mode for single-keypair scenarios.

## Concepts

| Term | Description |
|------|-------------|
| **org key** | Organization/team key pair. Authorizes projects via mazoku. |
| **member key** | Project key pair. Signs module files (machikado). |
| **machikado** | 96 bytes: `signature(64) ‖ member_pk(32)`. |
| **mazoku** | 96 bytes: `signature(64) ‖ org_pk(32)`, over `module_id ‖ 0x00 ‖ member_pk`. |

## Usage

### Generate keys

#### Java

```java
import java.nio.file.Files;
import java.nio.file.Paths;
import xyz.baaad.machikado.Key;

Key.Ed25519KeyPair orgKp = Key.generateKeyPair();
Key.Ed25519KeyPair memKp = Key.generateKeyPair();

Files.write(Paths.get("org_sk"), orgKp.privateKey);
Files.write(Paths.get("mem_sk"), memKp.privateKey);
```

#### Kotlin

```kotlin
import java.nio.file.Files
import java.nio.file.Paths
import xyz.baaad.machikado.Key

val orgKp: Key.Ed25519KeyPair = Key.generateKeyPair()
val memKp: Key.Ed25519KeyPair = Key.generateKeyPair()

Files.write(Paths.get("org_sk"), orgKp.privateKey)
Files.write(Paths.get("mem_sk"), memKp.privateKey)
```

### Sign (build time)

#### Java

```java
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import xyz.baaad.machikado.Sign;
import xyz.baaad.machikado.Utils;

String moduleId = "my_module";
List<Sign.FileEntry> entries = Utils.loadFolderFiles(
        Paths.get("module_dir"),
        new String[]{".git"},
        new String[]{},
        null
);

Sign.SignedBlob machikado = Sign.signFileEntries(entries, memKp.privateKey);
Files.write(Paths.get("machikado"), machikado.asBytes());

Sign.SignedBlob mazoku = Sign.signMazoku(moduleId, memKp.publicKey, orgKp.privateKey);
Files.write(Paths.get("mazoku"), mazoku.asBytes());
```

#### Kotlin

```kotlin
import java.util.List
import java.nio.file.Files
import java.nio.file.Paths
import xyz.baaad.machikado.Sign
import xyz.baaad.machikado.Utils

val moduleId = "my_module"
val entries: MutableList<Sign.FileEntry> = Utils.loadFolderFiles(
    Paths.get("module_dir"),
    arrayOf<String?>(".git"),
    arrayOf<String?>(),
    null
)

val machikado = Sign.signFileEntries(entries, memKp.privateKey)
Files.write(Paths.get("machikado"), machikado.asBytes())

val mazoku = Sign.signMazoku(moduleId, memKp.publicKey, orgKp.privateKey)
Files.write(Paths.get("mazoku"), mazoku.asBytes())
```

### Verify two-tier (device side)

#### Java

```java
import java.nio.file.Files;
import java.nio.file.Paths;
import xyz.baaad.machikado.Sign;
import xyz.baaad.machikado.Utils;

String moduleId = "my_module";
List<Sign.FileEntry> entries = Utils.loadFolderFiles(
        Paths.get("."),
        new String[]{},
        new String[]{"machikado", "mazoku"},
        null
);
byte[] machikado = Files.readAllBytes(Paths.get("machikado"));
byte[] mazoku = Files.readAllBytes(Paths.get("mazoku"));

Sign.VerifyResult result = Sign.verify(machikado, mazoku, entries, moduleId, expectedOrgPk);
if (!result.ok) {}
```

#### Kotlin

```kotlin
import java.nio.file.Files
import java.nio.file.Paths
import xyz.baaad.machikado.Sign
import xyz.baaad.machikado.Utils

val moduleId = "my_module"
val entries: MutableList<Sign.FileEntry> = Utils.loadFolderFiles(
    Paths.get("."),
    arrayOf<String?>(),
    arrayOf<String?>("machikado", "mazoku"),
    null
)
val machikado = Files.readAllBytes(Paths.get("machikado"))
val mazoku = Files.readAllBytes(Paths.get("mazoku"))

val result = Sign.verify(machikado, mazoku, entries, moduleId, expectedOrgPk)
if (!result.ok) {}
```

### Verify machikado-only (single keypair)

#### Java

```java
import java.nio.file.Paths;
import xyz.baaad.machikado.Key;
import xyz.baaad.machikado.Sign;
import xyz.baaad.machikado.Utils;

Key.Ed25519KeyPair kp = Key.generateKeyPair();
List<Sign.FileEntry> entries = Utils.loadFolderFiles(
        Paths.get("."),
        new String[]{},
        new String[]{"machikado"},
        null
);

Sign.SignedBlob machikado = Sign.signFileEntries(entries, kp.privateKey);

Sign.VerifyResult result = Sign.verifyMachikado(machikado, entries, expectedOrgPk);
if (!result.ok) {}
```

#### Kotlin

```kotlin
import java.nio.file.Paths
import xyz.baaad.machikado.Key
import xyz.baaad.machikado.Sign
import xyz.baaad.machikado.Utils

val kp = Key.generateKeyPair()
val entries: MutableList<Sign.FileEntry> = Utils.loadFolderFiles(
    Paths.get("."),
    arrayOf<String?>(),
    arrayOf<String?>("machikado"),
    null
)

val machikado = Sign.signFileEntries(entries, kp.privateKey)

val result = Sign.verifyMachikado(machikado, entries, expectedOrgPk)
if (!result.ok) {}
```

### File mapping

Map source paths to signed paths — for when `customize.sh` moves files at install time.

#### Java

```java
import java.util.Map;
import java.nio.file.Paths;
import xyz.baaad.machikado.Utils;

Utils.FileMapping mapping = new Utils.FileMapping();

// Single pair
mapping.insert("bin/zygiskd64", "bin/arm64-v8a/zygiskd");

// Multiple pairs
mapping.insert("bin/zygiskd32", "bin/armeabi-v7a/zygiskd");
mapping.insert("lib/libzygisk.so", "lib/armeabi-v7a/libzygisk.so");

// Iteration
for (Map.Entry<String, String> entry : mapping.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}

List<Utils.FileEntry> entries = Utils.loadFolderFiles(
        Paths.get("dir"),
        new String[]{},
        new String[]{},
        mapping
);
```

#### Kotlin

```kotlin
import java.util.Map
import java.nio.file.Paths
import xyz.baaad.machikado.Utils

val mapping = FileMapping()

// Single pair
mapping.insert("bin/zygiskd64", "bin/arm64-v8a/zygiskd")

// Multiple pairs
mapping.insert("bin/zygiskd32", "bin/armeabi-v7a/zygiskd")
mapping.insert("lib/libzygisk.so", "lib/armeabi-v7a/libzygisk.so")

// Iteration
for (entry in mapping.entrySet()) {
    println(entry.key + " -> " + entry.value)
}

val entries: MutableList<Utils.FileEntry> = Utils.loadFolderFiles(
    Paths.get("dir"),
    arrayOf<String?>(),
    arrayOf<String?>(),
    mapping
)
```

## API

| Function | Returns |
|----------|--------|
| `Key.generateKeyPair()` | `Key.Ed25519KeyPair` |
| `Sign.signFileEntries(List<FileEntry>, byte[])` | `Sign.SignedBlob` |
| `Sign.signMazoku(String, byte[], byte[])` | `Sign.SignedBlob` |
| `Sign.verify(byte[], byte[], List<FileEntry>, String, byte[])` | `Sign.VerifyResult` |
| `Sign.verifyMachikado(byte[], List<FileEntry>, byte[])` | `Sign.VerifyResult` |
| `Utils.loadFolderFiles(Path, List<String>, List<String>, FileMapping)` | `List<Sign.FileEntry>` |

`SignedBlob` is a 96-byte newtype with `.asBytes()`, `.fromBytes()`, `.signature`, `.publicKey`.

## Signing protocol

Compatible with ZygiskNext. Each file feeds into the signature as:

```
relative_path ‖ 0x00 ‖ file_size(LE u64) ‖ file_content
```

Accumulated in lexicographic order, signed once.

**mazoku** signing data: `module_id ‖ 0x00 ‖ project_public_key`, where `module_id` must match `^[a-zA-Z][a-zA-Z0-9._-]+$`.

Both `verify` and `verify_machikado` compare the embedded public key against a hardcoded expected key for integrity.

## License

* [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0)
