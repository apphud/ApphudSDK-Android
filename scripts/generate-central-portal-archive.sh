#!/bin/bash

# Script to generate Central Publisher Portal archive for ApphudSDK-Android
# This script creates a properly formatted archive that can be uploaded to
# https://central.sonatype.com/account

set -e  # Exit on any error

# Configuration - these should match your gradle.properties and sdk/build.gradle
GROUP_ID="com.apphud"
ARTIFACT_ID="ApphudSDK-Android"
VERSION=$(grep "sdkVersion=" gradle.properties | cut -d'=' -f2)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if we're in the right directory
    if [[ ! -f "gradle.properties" ]] || [[ ! -f "settings.gradle" ]]; then
        log_error "Please run this script from the project root directory"
        exit 1
    fi
    
    # Check if gpg is available
    if ! command -v gpg &> /dev/null; then
        log_warn "GPG not found. Signatures will not be generated."
        log_warn "Install GPG and configure your signing key for complete archive."
        GPG_AVAILABLE=false
    else
        GPG_AVAILABLE=true
        log_success "GPG found"
        
        # Read GPG configuration from local.properties
        if [[ -f "local.properties" ]]; then
            SIGNING_KEY_ID=$(grep "signing.keyId=" local.properties | cut -d'=' -f2)
            SIGNING_PASSWORD=$(grep "signing.password=" local.properties | cut -d'=' -f2)
            SIGNING_SECRET_KEY_RING=$(grep "signing.secretKeyRingFile=" local.properties | cut -d'=' -f2)
            
            # Remove escape characters from password (properties file format uses backslash escapes)
            SIGNING_PASSWORD=$(echo "$SIGNING_PASSWORD" | sed 's/\\//g')
            
            if [[ -n "$SIGNING_KEY_ID" ]] && [[ -n "$SIGNING_PASSWORD" ]]; then
                log_success "GPG signing configuration loaded from local.properties"
            else
                log_warn "GPG configuration incomplete in local.properties"
            fi
        fi
    fi
    
    # Check if md5sum/md5 is available
    if command -v md5sum &> /dev/null; then
        MD5_CMD="md5sum"
    elif command -v md5 &> /dev/null; then
        MD5_CMD="md5 -r"
    else
        log_error "Neither md5sum nor md5 command found"
        exit 1
    fi
    
    # Check if sha1sum/shasum is available
    if command -v sha1sum &> /dev/null; then
        SHA1_CMD="sha1sum"
    elif command -v shasum &> /dev/null; then
        SHA1_CMD="shasum -a 1"
    else
        log_error "Neither sha1sum nor shasum command found"
        exit 1
    fi
    
    log_success "Prerequisites check completed"
}

build_artifacts() {
    log_info "Building artifacts..."
    
    # Clean and build with Maven Central configuration
    # This uses the conditional publishing setup in sdk/build.gradle
    # - Without -PmavenCentral: Uses JitPack config (simple)
    # - With -PmavenCentral: Uses full Maven Central config (complete)
    ./gradlew clean
    ./gradlew -PmavenCentral sdk:publishToMavenLocal
    
    log_success "Artifacts built and published to local Maven repository"
}

create_archive_structure() {
    log_info "Creating archive structure..."
    
    # Define paths
    LOCAL_REPO="$HOME/.m2/repository"
    ARTIFACT_PATH=$(echo "${GROUP_ID}" | tr '.' '/')/${ARTIFACT_ID}/${VERSION}
    SOURCE_DIR="${LOCAL_REPO}/${ARTIFACT_PATH}"
    
    # Create working directories
    WORK_DIR="build/central-portal-archive"
    TARGET_DIR="${WORK_DIR}/${ARTIFACT_PATH}"
    
    rm -rf "$WORK_DIR"
    mkdir -p "$TARGET_DIR"
    
    # Check if source directory exists
    if [[ ! -d "$SOURCE_DIR" ]]; then
        log_error "Source directory not found: $SOURCE_DIR"
        log_error "Make sure 'gradle sdk:publishToMavenLocal' completed successfully"
        exit 1
    fi
    
    # Copy all files from local Maven repository
    cp -r "$SOURCE_DIR"/* "$TARGET_DIR/"
    
    log_success "Archive structure created in $WORK_DIR"
    echo "Source: $SOURCE_DIR"
    echo "Target: $TARGET_DIR"
}

generate_checksums_and_signatures() {
    log_info "Generating checksums and signatures..."
    
    cd "$TARGET_DIR"
    
    # Find all main artifact files (excluding already generated checksums and signatures)
    find . -name "*.aar" -o -name "*.pom" -o -name "*.jar" -o -name "*.module" | while read -r file; do
        if [[ ! "$file" =~ \.(asc|md5|sha1)$ ]]; then
            log_info "Processing: $file"
            
            # Generate MD5 checksum
            $MD5_CMD "$file" | awk '{print $1}' > "${file}.md5"
            log_success "Generated MD5 for $file"
            
            # Generate SHA1 checksum
            $SHA1_CMD "$file" | awk '{print $1}' > "${file}.sha1"
            log_success "Generated SHA1 for $file"
            
            # Generate GPG signature if available
            if [[ "$GPG_AVAILABLE" == true ]]; then
                # Use password and key ID from local.properties if available
                if [[ -n "$SIGNING_PASSWORD" ]] && [[ -n "$SIGNING_KEY_ID" ]]; then
                    # Sign with password using passphrase-fd
                    if echo "$SIGNING_PASSWORD" | gpg --batch --yes --passphrase-fd 0 --pinentry-mode loopback \
                        --local-user "$SIGNING_KEY_ID" --armor --detach-sign --output "${file}.asc" "$file" 2>/dev/null; then
                        log_success "Generated GPG signature for $file"
                    else
                        log_warn "Failed to generate GPG signature for $file"
                        log_warn "Check your signing.keyId and signing.password in local.properties"
                    fi
                else
                    # Fallback: try without password (for keys with gpg-agent)
                    if gpg --armor --detach-sign --output "${file}.asc" "$file" 2>/dev/null; then
                        log_success "Generated GPG signature for $file"
                    else
                        log_warn "Failed to generate GPG signature for $file"
                        log_warn "Configure signing.keyId and signing.password in local.properties"
                    fi
                fi
            fi
        fi
    done
    
    cd - > /dev/null
}

create_zip_archive() {
    log_info "Creating ZIP archive..."
    
    cd "$WORK_DIR"
    
    ARCHIVE_NAME="${ARTIFACT_ID}-${VERSION}-central-portal.zip"
    
    # Create the zip archive
    GROUP_PATH=$(echo "${GROUP_ID}" | tr '.' '/')
    zip -r "$ARCHIVE_NAME" "${GROUP_PATH}/"
    
    # Move to project root
    mv "$ARCHIVE_NAME" "../$ARCHIVE_NAME"
    
    cd - > /dev/null
    
    log_success "Archive created: build/$ARCHIVE_NAME"
}

verify_archive() {
    log_info "Verifying archive contents..."
    
    ARCHIVE_NAME="${ARTIFACT_ID}-${VERSION}-central-portal.zip"
    local has_errors=false
    
    echo ""
    echo "Archive contents:"
    unzip -l "build/$ARCHIVE_NAME"
    echo ""
    
    # Define the 5 required artifacts
    local artifacts=(
        "${ARTIFACT_ID}-${VERSION}.aar"
        "${ARTIFACT_ID}-${VERSION}.pom"
        "${ARTIFACT_ID}-${VERSION}-sources.jar"
        "${ARTIFACT_ID}-${VERSION}-javadoc.jar"
        "${ARTIFACT_ID}-${VERSION}.module"
    )
    
    local artifact_count=0
    
    # Check each required artifact exists
    log_info "Checking required artifacts..."
    for artifact in "${artifacts[@]}"; do
        if unzip -l "build/$ARCHIVE_NAME" | grep -q "$artifact\$"; then
            log_success "✓ Found: $artifact"
            artifact_count=$((artifact_count + 1))
        else
            log_error "✗ Missing: $artifact"
            has_errors=true
        fi
    done
    
    echo ""
    
    # Check for signatures and checksums
    sig_count=$(unzip -l "build/$ARCHIVE_NAME" | grep -c "\.asc$" || true)
    md5_count=$(unzip -l "build/$ARCHIVE_NAME" | grep -c "\.md5$" || true)
    sha1_count=$(unzip -l "build/$ARCHIVE_NAME" | grep -c "\.sha1$" || true)
    
    log_info "File counts:"
    log_info "  Artifacts: $artifact_count/5"
    log_info "  Signatures (.asc): $sig_count"
    log_info "  MD5 checksums (.md5): $md5_count"
    log_info "  SHA1 checksums (.sha1): $sha1_count"
    echo ""
    
    # Validate signatures - Maven Central requires signatures for ALL artifacts
    log_info "Validating signatures (required by Maven Central)..."
    
    if [[ $sig_count -lt 5 ]]; then
        log_error "Missing signatures! Found $sig_count but expected 5 (one per artifact)"
        has_errors=true
    fi
    
    # Check each artifact has its signature
    for artifact in "${artifacts[@]}"; do
        local sig_file="${artifact}.asc"
        if unzip -l "build/$ARCHIVE_NAME" | grep -q "$sig_file\$"; then
            log_success "✓ Signature exists: $(basename "$sig_file")"
        else
            log_error "✗ Missing signature: $(basename "$sig_file")"
            has_errors=true
        fi
    done
    
    echo ""
    
    # Validate checksums
    log_info "Validating checksums..."
    if [[ $md5_count -eq 5 ]]; then
        log_success "✓ All MD5 checksums present ($md5_count/5)"
    else
        log_error "✗ MD5 checksum mismatch: found $md5_count, expected 5"
        has_errors=true
    fi
    
    if [[ $sha1_count -eq 5 ]]; then
        log_success "✓ All SHA1 checksums present ($sha1_count/5)"
    else
        log_error "✗ SHA1 checksum mismatch: found $sha1_count, expected 5"
        has_errors=true
    fi
    
    echo ""
    
    # Final validation result
    if [[ "$has_errors" == true ]]; then
        log_error "=========================================="
        log_error "Archive validation FAILED!"
        log_error "=========================================="
        log_error "Please fix the errors above before uploading to Maven Central."
        log_error "Missing signatures will cause Maven Central to reject your upload."
        echo ""
        exit 1
    else
        log_success "=========================================="
        log_success "Archive validation PASSED!"
        log_success "=========================================="
        log_success "All required files are present:"
        log_success "  ✓ 5 artifacts"
        log_success "  ✓ 5 GPG signatures (.asc)"
        log_success "  ✓ 5 MD5 checksums (.md5)"
        log_success "  ✓ 5 SHA1 checksums (.sha1)"
        log_success "Total: 23 files (5 artifacts × 4 files each + 3 directories)"
        echo ""
    fi
}

print_usage_instructions() {
    echo ""
    echo "======================================"
    echo "Central Publisher Portal Upload Ready!"
    echo "======================================"
    echo ""
    echo "Your archive is ready for upload:"
    echo "📦 File: build/${ARTIFACT_ID}-${VERSION}-central-portal.zip"
    echo ""
    echo "Next steps:"
    echo "1. Go to https://central.sonatype.com/account"
    echo "2. Navigate to the 'Publish' section"
    echo "3. Upload the generated ZIP file"
    echo "4. Verify the namespace '$GROUP_ID' is claimed by your account"
    echo "5. Submit for publication"
    echo ""
    echo "Archive structure follows Maven Repository Layout:"
    GROUP_PATH=$(echo "${GROUP_ID}" | tr '.' '/')
    echo "${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/"
    echo "├── ${ARTIFACT_ID}-${VERSION}.aar (+ .asc, .md5, .sha1)"
    echo "├── ${ARTIFACT_ID}-${VERSION}.pom (+ .asc, .md5, .sha1)"
    echo "├── ${ARTIFACT_ID}-${VERSION}.module (+ .asc, .md5, .sha1)"
    echo "├── ${ARTIFACT_ID}-${VERSION}-sources.jar (+ .asc, .md5, .sha1)"
    echo "└── ${ARTIFACT_ID}-${VERSION}-javadoc.jar (+ .asc, .md5, .sha1)"
    echo ""
    if [[ "$GPG_AVAILABLE" != true ]]; then
        echo "⚠️  Note: GPG signatures were not generated. Install and configure GPG for complete archive."
    fi
}

# Main execution
main() {
    echo "======================================"
    echo "Central Publisher Portal Archive Generator"
    echo "ApphudSDK-Android v${VERSION}"
    echo "======================================"
    echo ""
    
    check_prerequisites
    build_artifacts
    create_archive_structure
    generate_checksums_and_signatures
    create_zip_archive
    verify_archive
    print_usage_instructions
    
    log_success "Archive generation completed successfully!"
}

# Run main function
main "$@"
