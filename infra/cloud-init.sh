#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

# ── System updates ───────────────────────────────────────────────────────
apt-get update && apt-get -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" upgrade -y

# ── Docker (official install — works on x86 and ARM) ────────────────────
curl -fsSL https://get.docker.com | sh

# ── sqlite3 (for backup scripts) ────────────────────────────────────────
apt-get install -y sqlite3 rsync

# ── Deploy user with docker access ──────────────────────────────────────
useradd -m -s /bin/bash -G docker,sudo deploy
mkdir -p /home/deploy/.ssh
cp /root/.ssh/authorized_keys /home/deploy/.ssh/
chown -R deploy:deploy /home/deploy/.ssh
chmod 700 /home/deploy/.ssh
chmod 600 /home/deploy/.ssh/authorized_keys
echo 'deploy ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/deploy
chmod 440 /etc/sudoers.d/deploy

# ── 2GB swap (safety net for Docker image builds) ───────────────────────
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# ── UFW firewall (belt-and-suspenders with cloud firewall) ──────────────
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# ── App directory ────────────────────────────────────────────────────────
mkdir -p /opt/deadly /opt/deadly/api-data /opt/deadly/ui-out
chown -R deploy:deploy /opt/deadly

# ── Signal completion ────────────────────────────────────────────────────
touch /opt/deadly/.cloud-init-complete
