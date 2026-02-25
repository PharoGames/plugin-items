#!/bin/bash
# Server image push script - pushes Docker image to GHCR or local registry
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

log_step "Pushing Server Image"

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
    # CI: Push to GHCR
    log_info "Pushing to GHCR..."
    
    if ! docker_push "$image_tag"; then
        die "Failed to push image to GHCR"
    fi
    
    # Also tag as latest if on main branch
    if [[ "$also_tag_latest" == "true" ]] || [[ "$(get_branch_name)" == "main" ]]; then
        # Extract base name and create latest tag
        local base_name
        base_name=$(echo "$image_tag" | sed 's/:.*$//')
        local latest_tag="${base_name}:latest"
        
        log_info "Tagging as latest: $latest_tag"
        
        if docker_tag "$image_tag" "$latest_tag"; then
            if docker_push "$latest_tag"; then
                log_success "Latest tag pushed"
            else
                log_warning "Failed to push latest tag (non-fatal)"
            fi
        fi
    fi
    
    # Get image digest for metadata
    local digest
    digest=$(get_image_digest "$image_tag" || echo "unknown")
    
    if [[ -n "$digest" ]] && [[ "$digest" != "unknown" ]]; then
        echo "image_digest=$digest" >> "$GITHUB_OUTPUT"
        log_info "Image digest: $digest"
    fi
    
    log_success "Image pushed to GHCR"
else
    # Local: Push to local registry
    log_info "Pushing to local registry..."
    
    if ! docker_push "$image_tag"; then
        log_warning "Failed to push to local registry (is registry running?)"
        log_info "Image is available locally: $image_tag"
    else
        log_success "Image pushed to local registry"
    fi
fi

log_success "Server image push completed"

