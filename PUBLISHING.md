# Publishing Guide

This document describes how to publish the ApphudSDK-Android library to different repositories.

## Overview

The SDK supports two publishing targets:
- **JitPack** (default) - Automatic publishing via Git tags
- **Maven Central** - Manual publishing with signing and full metadata

## Publishing Methods

### JitPack Publishing (Default)

JitPack automatically builds and publishes the library when you create a Git tag.

#### Prerequisites
- Git repository with proper tags
- Android project with `maven-publish` plugin
- Valid `jitpack.yml` configuration

#### How to Publish
1. Create and push a Git tag:
```bash
git tag 3.4.6
git push origin 3.4.6
```

2. JitPack will automatically:
   - Detect the new tag
   - Clone the repository
   - Run `./gradlew publish`
   - Make the artifact available at `https://jitpack.io/#apphud/ApphudSDK-Android/3.4.6`

#### Usage
Users can include the library in their projects:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.apphud:ApphudSDK-Android:3.4.6'
}
```

#### Configuration Files
- `jitpack.yml` - Specifies JDK version (OpenJDK 17)
- `sdk/build.gradle` - Contains JitPack publishing configuration (active by default)

### Maven Central Publishing

Maven Central publishing requires manual execution with proper credentials and signing.

#### Prerequisites
- Sonatype OSSRH account
- GPG signing keys configured
- Credentials in `local.properties` or environment variables:
```properties
# local.properties
ossrhUsername=your-username
ossrhPassword=your-password
sonatypeStagingProfileId=your-profile-id
signing.keyId=your-key-id
signing.password=your-key-password
signing.secretKeyRingFile=/path/to/secring.gpg
```

#### How to Publish
1. Build and publish to Sonatype staging:
```bash
./gradlew -PmavenCentral publishToSonatype
```

2. Generate Central Portal archive (optional):
```bash
./gradlew -PmavenCentral generateCentralPortalArchive
```

3. Close and release staging repository via Sonatype Nexus UI or:
```bash
./gradlew -PmavenCentral closeAndReleaseRepository
```

#### What Gets Published
- Main AAR file
- Sources JAR
- Javadoc JAR
- POM with full metadata
- GPG signatures for all files
- SHA1/MD5 checksums

#### Usage
Users can include the library from Maven Central:
```gradle
dependencies {
    implementation 'com.apphud:ApphudSDK-Android:3.4.6'
}
```

## Configuration Details

### Conditional Publishing Logic

The `sdk/build.gradle` uses conditional logic to determine publishing mode:

```gradle
def isMavenCentralBuild = project.hasProperty('mavenCentral')
def isJitPackBuild = !isMavenCentralBuild

// JitPack by default
if (isJitPackBuild) {
    afterEvaluate {
        publishing {
            publications {
                release(MavenPublication) {
                    from components.release
                }
            }
        }
    }
}

// Maven Central with -PmavenCentral flag
if (isMavenCentralBuild) {
    apply from: "${rootProject.projectDir}/scripts/publish-module.gradle"
}
```

### File Structure
```
├── sdk/build.gradle              # Main module with conditional publishing
├── scripts/publish-module.gradle # Full Maven Central configuration
├── scripts/publish-root.gradle   # Sonatype repository configuration
├── jitpack.yml                   # JitPack build environment
└── PUBLISHING.md                 # This guide
```

## Troubleshooting

### JitPack Issues
- **Build fails**: Check `jitpack.yml` JDK version compatibility
- **Artifacts not found**: Verify tag was pushed to GitHub
- **Build timeout**: Large projects may need build optimization

### Maven Central Issues
- **Signing fails**: Verify GPG key configuration and passwords
- **Upload fails**: Check Sonatype credentials and network connectivity
- **POM validation**: Ensure all required metadata is present

### Common Commands
```bash
# Test JitPack configuration locally
./gradlew publish

# Test Maven Central configuration locally
./gradlew -PmavenCentral publishToMavenLocal

# Check available publishing tasks
./gradlew tasks --group publishing

# Clean build artifacts
./gradlew clean
```

## Release Process

### Recommended Workflow
1. **Development**: Work on feature branches
2. **Testing**: Merge to main/master branch
3. **JitPack Release**:
   - Create Git tag
   - Verify JitPack build success
4. **Maven Central Release** (optional):
   - Run Maven Central publishing
   - Verify artifacts in Sonatype staging
   - Release to production

### Version Management
- Use semantic versioning (e.g., `3.4.6`)
- Git tags should match version in `gradle.properties`
- Update `CHANGELOG.md` before releasing

## Environment Variables

JitPack automatically sets `JITPACK=true` during builds, but the current configuration doesn't rely on this variable. Instead, it uses Gradle properties for explicit control.

| Variable | Purpose | Default |
|----------|---------|---------|
| `-PmavenCentral` | Enable Maven Central publishing | Not set (JitPack mode) |
| `ossrhUsername` | Sonatype username | Required for Maven Central |
| `ossrhPassword` | Sonatype password | Required for Maven Central |
| `signing.keyId` | GPG key ID | Required for Maven Central |