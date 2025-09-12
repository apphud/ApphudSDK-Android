
# Central Publisher Portal Archive Generation

This directory contains scripts to generate properly formatted archives for uploading to the Central Publisher Portal at https://central.sonatype.com/account.

## Files

- `generate-central-portal-archive.sh` - Main script that handles the entire archive generation process
- `publish-module.gradle` - Gradle script with Maven publishing configuration and archive generation task
- `publish-root.gradle` - Root-level publishing configuration

## Usage Options

### Option 1: Using the Shell Script (Recommended)

From the project root directory:

```bash
# Simple wrapper
./generate-archive.sh

# Or run directly
./scripts/generate-central-portal-archive.sh
```

### Option 2: Using Gradle Task

```bash
# Generate archive using Gradle
./gradlew sdk:generateCentralPortalArchive
```

## What the Scripts Do

1. **Build Project**: Clean build and compile all artifacts
2. **Publish Locally**: Publish to local Maven repository (`~/.m2/repository`)
3. **Create Structure**: Set up proper Maven Repository Layout directory structure
4. **Generate Files**: Create all required files:
   - Main artifacts (`.aar`, `.pom`, `.module`, `-sources.jar`, `-javadoc.jar`)
   - MD5 checksums (`.md5`)
   - SHA1 checksums (`.sha1`)
   - GPG signatures (`.asc`) - if GPG is configured
5. **Create Archive**: Package everything into a ZIP file ready for upload

## Output

The script generates:
```
build/ApphudSDK-Android-{VERSION}-central-portal.zip
```

This archive contains the Maven Repository Layout structure:
```
com/
└── apphud/
    └── ApphudSDK-Android/
        └── {VERSION}/
            ├── ApphudSDK-Android-{VERSION}.aar
            ├── ApphudSDK-Android-{VERSION}.aar.asc
            ├── ApphudSDK-Android-{VERSION}.aar.md5
            ├── ApphudSDK-Android-{VERSION}.aar.sha1
            ├── ApphudSDK-Android-{VERSION}.pom
            ├── ApphudSDK-Android-{VERSION}.pom.asc
            ├── ApphudSDK-Android-{VERSION}.pom.md5
            ├── ApphudSDK-Android-{VERSION}.pom.sha1
            ├── ApphudSDK-Android-{VERSION}.module
            ├── ApphudSDK-Android-{VERSION}.module.asc
            ├── ApphudSDK-Android-{VERSION}.module.md5
            ├── ApphudSDK-Android-{VERSION}.module.sha1
            ├── ApphudSDK-Android-{VERSION}-sources.jar
            ├── ApphudSDK-Android-{VERSION}-sources.jar.asc
            ├── ApphudSDK-Android-{VERSION}-sources.jar.md5
            ├── ApphudSDK-Android-{VERSION}-sources.jar.sha1
            ├── ApphudSDK-Android-{VERSION}-javadoc.jar
            ├── ApphudSDK-Android-{VERSION}-javadoc.jar.asc
            ├── ApphudSDK-Android-{VERSION}-javadoc.jar.md5
            └── ApphudSDK-Android-{VERSION}-javadoc.jar.sha1
```

## Prerequisites

- **Java/Android SDK**: For building the project
- **GPG** (optional but recommended): For generating signatures
  - Install: `brew install gnupg` (macOS) or `apt-get install gnupg` (Linux)
  - Configure your signing key
- **Command line tools**: `md5sum`/`md5`, `sha1sum`/`shasum` (usually pre-installed)

## GPG Configuration

To generate proper signatures, you need to configure GPG:

1. **Generate a key** (if you don't have one):
   ```bash
   gpg --gen-key
   ```

2. **List keys** to find your key ID:
   ```bash
   gpg --list-secret-keys --keyid-format LONG
   ```

3. **Export public key** for uploading to key servers:
   ```bash
   gpg --armor --export YOUR_KEY_ID
   ```

4. **Upload to key servers**:
   ```bash
   gpg --send-keys YOUR_KEY_ID
   ```

## Upload to Central Publisher Portal

1. Go to https://central.sonatype.com/account
2. Navigate to "Publish" section
3. Upload the generated ZIP file
4. Verify namespace ownership (`com.apphud`)
5. Submit for publication

## Troubleshooting

- **"Source directory not found"**: Run `./gradlew clean sdk:publishToMavenLocal` first
- **GPG signing fails**: Ensure GPG is installed and configured with a valid key
- **Permission denied**: Make sure scripts are executable (`chmod +x script-name.sh`)
- **Wrong directory**: Run scripts from the project root directory

## Configuration

The scripts read configuration from:
- `gradle.properties` - Version number (`sdkVersion`)
- `sdk/build.gradle` - Group ID, Artifact ID, and other metadata

Current configuration:
- Group ID: `com.apphud`
- Artifact ID: `ApphudSDK-Android`
- Version: Reads from `gradle.properties`
