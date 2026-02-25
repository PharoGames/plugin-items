#!/bin/bash
# Server image build script - builds Docker image with plugins baked in
# Works in CI environments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/docker.sh"
source "$SCRIPT_DIR/../lib/github.sh"

# Default values
server_image_dir="."
server_type=""
image_tag=""
purpur_version="${PURPUR_VERSION:-1.21.11}"
plugins_json="${PLUGINS_VERSIONS_JSON:-[]}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --server-image-dir)
            server_image_dir="$2"
            shift 2
            ;;
        --server-type)
            server_type="$2"
            shift 2
            ;;
        --image-tag)
            image_tag="$2"
            shift 2
            ;;
        --purpur-version)
            purpur_version="$2"
            shift 2
            ;;
        --plugins-json)
            plugins_json="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Building Server Image"
log_info "Server image directory: $server_image_dir"

# Change to server image directory
cd "$server_image_dir"

# Auto-detect server type from directory name if not provided
if [[ -z "$server_type" ]]; then
    server_type=$(basename "$server_image_dir" | sed 's/^server-image-//')
    log_info "Auto-detected server type: $server_type"
fi

if [[ -z "$server_type" ]]; then
    die "Server type not specified and could not be auto-detected"
fi

# Verify Dockerfile exists
if [[ ! -f "Dockerfile" ]]; then
    die "Dockerfile not found in $server_image_dir"
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
    
    if is_ci; then
        # CI: use branch-sha tag
        image_tag="${registry}/server-image-${server_type}:${branch}-${short_sha}"
    else
        # Local: use dev tag
        image_tag="${registry}/server-image-${server_type}:dev"
    fi
fi

log_info "Server type: $server_type"
log_info "Image tag: $image_tag"
log_info "Purpur version: $purpur_version"

# Get GitHub token for plugin downloads
local github_token=""
local github_owner=""

if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    github_token="$GITHUB_TOKEN"
    github_owner=$(get_repo_owner)
elif [[ -n "${APP_ID:-}" ]]; then
    log_info "Generating GitHub App token..."
    github_token=$(get_github_token)
    github_owner=$(get_repo_owner)
else
    log_warning "No GitHub authentication available, plugins may fail to download"
    github_owner="pharogames"
fi

# Build Docker image with build args
log_info "Building Docker image..."

local build_args=(
    --build-arg "PURPUR_VERSION=${purpur_version}"
    --build-arg "PLUGINS_VERSIONS_JSON=${plugins_json}"
)

if [[ -n "$github_token" ]]; then
    build_args+=(
        --build-arg "GITHUB_TOKEN=${github_token}"
        --build-arg "GITHUB_OWNER=${github_owner}"
    )
fi

if ! docker_build "$image_tag" "Dockerfile" "." "${build_args[@]}"; then
    die "Failed to build Docker image"
fi

# Export metadata for subsequent steps
if is_ci; then
    # GitHub Actions output
    echo "image_tag=$image_tag" >> "$GITHUB_OUTPUT"
    echo "server_type=$server_type" >> "$GITHUB_OUTPUT"
else
    # Local: write to file
    cat > "build-metadata.env" <<EOF
IMAGE_TAG=$image_tag
SERVER_TYPE=$server_type
EOF
    log_info "Build metadata written to: build-metadata.env"
fi

log_success "Server image built successfully"
log_success "Image: $image_tag"

