#!/bin/sh
# Daily database dump, kept 14 days. Installed as /etc/cron.d/ggpower-backup.
set -eu
cd /opt/ggpower
mkdir -p backups
/usr/local/bin/docker compose exec -T db pg_dump -U ggpower -d ggpower -Fc > "backups/ggpower-$(date +%Y%m%d).dump.tmp"
mv "backups/ggpower-$(date +%Y%m%d).dump.tmp" "backups/ggpower-$(date +%Y%m%d).dump"
find backups -name 'ggpower-*.dump' -mtime +14 -delete
