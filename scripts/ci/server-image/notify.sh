#!/bin/bash
# Server image notify script - notifies orchestrator of new image
# Only runs in CI (skipped locally)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

# Default values
orchestrator_url="${ORCHESTRATOR_URL:-}"
server_type=""
image_tag=""
reason="${TRIGGER_REASON:-manual}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --orchestrator-url)
            orchestrator_url="$2"
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
        --reason)
            reason="$2"
            shift 2
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

log_step "Notifying Orchestrator"

# Get metadata from previous steps if not provided
if [[ -z "$server_type" ]] || [[ -z "$image_tag" ]]; then
    if [[ -f "$GITHUB_OUTPUT" ]]; then
        [[ -z "$server_type" ]] && server_type=$(grep "^server_type=" "$GITHUB_OUTPUT" | cut -d'=' -f2-)
        [[ -z "$image_tag" ]] && image_tag=$(grep "^image_tag=" "$GITHUB_OUTPUT" | cut -d'=' -f2-)
    fi
fi

if [[ -z "$server_type" ]]; then
    die "Server type not provided and could not be found in metadata"
fi

if [[ -z "$image_tag" ]]; then
    die "Image tag not provided and could not be found in metadata"
fi

log_info "Server type: $server_type"
log_info "Image tag: $image_tag"
log_info "Reason: $reason"

# Determine orchestrator URL if not provided
if [[ -z "$orchestrator_url" ]]; then
    # Try to get from environment or use default
    if [[ -n "${ORCHESTRATOR_URL:-}" ]]; then
        orchestrator_url="$ORCHESTRATOR_URL"
    else
        log_warning "Orchestrator URL not provided, using default"
        orchestrator_url="http://orchestrator.services.svc.cluster.local:3000"
    fi
fi

log_info "Orchestrator URL: $orchestrator_url"

# Notify orchestrator via API
log_info "Sending notification to orchestrator..."

local payload
payload=$(cat <<EOF
{
  "serverType": "${server_type}",
  "imageTag": "${image_tag}",
  "reason": "${reason}",
  "timestamp": "$(get_timestamp)"
}
EOF
)

local response
response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "${orchestrator_url}/webhook/image-updated" || echo "000")

local http_code
http_code=$(echo "$response" | tail -n 1)
local body
body=$(echo "$response" | head -n -1)

if [[ "$http_code" == "200" ]] || [[ "$http_code" == "202" ]]; then
    log_success "Orchestrator notified successfully"
    log_info "Response: $body"
elif [[ "$http_code" == "000" ]]; then
    log_warning "Failed to reach orchestrator (connection failed)"
    log_info "Orchestrator will detect the new image via ArgoCD Image Updater"
else
    log_warning "Orchestrator notification returned HTTP $http_code"
    log_info "Response: $body"
    log_info "Orchestrator will detect the new image via ArgoCD Image Updater"
fi

# Note: We don't fail the build if orchestrator notification fails,
# as ArgoCD Image Updater will detect the new image anyway
log_success "Server image notification completed"

