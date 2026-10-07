#!/bin/sh
# Manage broker accounts.
#   ./mqtt-user.sh add <serial>      create or reset a datalogger account; prints the new password once
#   ./mqtt-user.sh remove <serial>   delete an account (the device is disconnected on its next login)
#   ./mqtt-user.sh list
set -eu
cd /opt/ggpower
D="/usr/local/bin/docker compose"
case "${1:-}" in
  add)
    name="${2:?serial}"
    echo "$name" | grep -Eq '^[A-Za-z0-9_-]{1,64}$' || { echo "invalid name"; exit 1; }
    [ "$name" = backend ] && { echo "use the .env file for the backend account"; exit 1; }
    pw=$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
    $D exec -T -u 1883:1883 mqtt mosquitto_passwd -b /mosquitto/config/passwd "$name" "$pw"
    $D kill -s HUP mqtt >/dev/null
    echo "user: $name"
    echo "password: $pw"
    ;;
  remove)
    $D exec -T -u 1883:1883 mqtt mosquitto_passwd -D /mosquitto/config/passwd "${2:?serial}"
    $D kill -s HUP mqtt >/dev/null
    ;;
  list)
    cut -d: -f1 mqtt/config/passwd
    ;;
  *) sed -n 2,5p "$0"; exit 1 ;;
esac
