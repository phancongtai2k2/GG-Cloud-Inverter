#!/bin/sh
# Copies the web server's current TLS certificate to the broker and reloads it when it changed.
# Runs daily from /etc/cron.d/ggpower-backup (certificates renew about every 60 days).
set -eu
cd /opt/ggpower
. ./.env
src="data/caddy/caddy/certificates/acme-v02.api.letsencrypt.org-directory/$SITE_ADDRESS"
[ -f "$src/$SITE_ADDRESS.crt" ] || { echo "no certificate for $SITE_ADDRESS yet"; exit 1; }
mkdir -p mqtt/certs
if ! cmp -s "$src/$SITE_ADDRESS.crt" mqtt/certs/fullchain.crt; then
  install -m 644 -o 1883 -g 1883 "$src/$SITE_ADDRESS.crt" mqtt/certs/fullchain.crt
  install -m 600 -o 1883 -g 1883 "$src/$SITE_ADDRESS.key" mqtt/certs/privkey.key
  /usr/local/bin/docker compose kill -s HUP mqtt >/dev/null 2>&1 || true
  echo "certificate updated"
fi
