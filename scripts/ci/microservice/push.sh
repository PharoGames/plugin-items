#!/bin/bash
# Microservice push script - pushes Docker image to GHCR or local registry
# Works in CI environments

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/docker.sh"

# Default values
image_tag=""
also_tag_latest="false"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --image-tag)
            image_tag="$2"
            shift 2
            ;;
        --also-tag-latest)
            also_tag_latest="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Pushing Microservice Image"

# Get image tag from build metadata if not provided
if [[ -z "$image_tag" ]]; then
    if is_ci && [[ -f "$GITHUB_OUTPUT" ]]; then
        image_tag=$(grep "^image_tag=" "$GITHUB_OUTPUT" | cut -d'=' -f2-)
    elif [[ -f "build-metadata.env" ]]; then
        source "build-metadata.env"
        image_tag="$IMAGE_TAG"
    else
        die "Image tag not provided and could not be found in metadata"
    fi
fi

log_info "Image tag: $image_tag"

if is_ci; then
    # CI: Image was already pushed by buildx in multi-arch build
    # But we still tag and push latest if needed
    
    # Tag as latest if on main branch
    if [[ "$also_tag_latest" == "true" ]] || [[ "$(get_branch_name)" == "main" ]]; then
        # Extract base name and create latest tag
        local base_name
        base_name=$(echo "$image_tag" | sed 's/:.*$//')
        local latest_tag="${base_name}:latest"
        
        log_info "Building and pushing latest tag: $latest_tag"
        
        # For multi-arch, we need to rebuild with the latest tag
        # This is a limitation of buildx --load not supporting multi-arch
        if docker buildx version >/dev/null 2>&1; then
            # Use the same Dockerfile that was used for the build
            local dockerfile="${DOCKERFILE:-Dockerfile}"
            
            # Ensure buildx builder exists
            if ! docker buildx inspect multiarch-builder >/dev/null 2>&1; then
                docker buildx create --name multiarch-builder --driver docker-container --use
            else
                docker buildx use multiarch-builder
            fi
            
            # Login to registry
            if [[ -n "${GITHUB_TOKEN:-}" ]]; then
                echo "$GITHUB_TOKEN" | docker login ghcr.io -u "${GITHUB_ACTOR:-ci}" --password-stdin
            fi
            
            # Build and push latest tag
            docker buildx build \
                --platform linux/amd64,linux/arm64 \
                -f "$dockerfile" \
                -t "$latest_tag" \
                --push \
                . || log_warning "Failed to push latest tag (non-fatal)"
        fi
    fi
    
    log_success "Image available in GHCR"
fi

log_success "Microservice push completed"

