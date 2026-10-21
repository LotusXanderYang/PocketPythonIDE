#!/usr/bin/env bash
# Install the Linux-side tools once Ubuntu on WSL2 is running.
set -euo pipefail

if [[ "$(uname -s)" != Linux ]]; then
    echo "This script must run inside Ubuntu/WSL2" >&2
    exit 1
fi
if grep -qi microsoft /proc/version && [[ "$(uname -r)" != *WSL2* ]]; then
    echo "WSL2 is required for Docker Engine" >&2
    exit 1
fi

sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    docker.io git python3 python3-venv ca-certificates curl
if command -v systemctl >/dev/null && systemctl is-system-running >/dev/null 2>&1; then
    sudo systemctl enable --now docker
else
    sudo service docker start
fi
sudo docker info >/dev/null
echo "Linux Docker builder is ready"
