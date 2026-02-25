#!/bin/bash
# Microservice build script - builds Docker image for Node.js/NestJS microservices
# Works in CI environments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/docker.sh"

# Default values
service_dir="."
service_name=""
image_tag=""
dockerfile="Dockerfile"
use_multiarch="true"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --service-dir)
            service_dir="$2"
            shift 2
            ;;
        --service-name)
            service_name="$2"
            shift 2
            ;;
        --image-tag)
            image_tag="$2"
            shift 2
            ;;
        --dockerfile)
            dockerfile="$2"
            shift 2
            ;;
        --multiarch)
            use_multiarch="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Building Microservice"
log_info "Service directory: $service_dir"

# Change to service directory
cd "$service_dir"

# Auto-detect service name from directory if not provided
if [[ -z "$service_name" ]]; then
    service_name=$(basename "$service_dir")
    log_info "Auto-detected service name: $service_name"
fi

if [[ -z "$service_name" ]]; then
    die "Service name not specified and could not be auto-detected"
fi

# Verify Dockerfile exists
if [[ ! -f "$dockerfile" ]]; then
    die "Dockerfile not found: $dockerfile"
fi

# Generate image tag if not provided
if [[ -z "$image_tag" ]]; then
    local registry
    registry=$(get_docker_registry)
    local branch
    branch=$(get_branch_name)
    local sha
    sha=$(get_commit_sha)
    local short_sha="${sha:0:7}"
    
    image_tag="${registry}/${service_name}:${branch}-${short_sha}"
fi

log_info "Service name: $service_name"
log_info "Image tag: $image_tag"
log_info "Dockerfile: $dockerfile"

# Build Docker image
log_info "Building Docker image..."

if is_ci && [[ "$use_multiarch" == "true" ]]; then
    # CI: Multi-arch build
    log_info "Building multi-arch image (amd64, arm64)..."
    
    if ! docker_build_and_push_multiarch "$image_tag" "$dockerfile" "."; then
        die "Failed to build multi-arch image"
    fi
else
    # Local or single-arch: Standard build
    if ! docker_build "$image_tag" "$dockerfile" "."; then
        die "Failed to build Docker image"
    fi
fi

# Export metadata for subsequent steps
if is_ci; then
    # GitHub Actions output
    echo "image_tag=$image_tag" >> "$GITHUB_OUTPUT"
    echo "service_name=$service_name" >> "$GITHUB_OUTPUT"
else
    # Local: write to file
    cat > "build-metadata.env" <<EOF
IMAGE_TAG=$image_tag
SERVICE_NAME=$service_name
EOF
    log_info "Build metadata written to: build-metadata.env"
fi

log_success "Microservice built successfully"
log_success "Image: $image_tag"

