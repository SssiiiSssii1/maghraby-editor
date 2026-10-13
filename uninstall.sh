#!/usr/bin/env bash
if [ "$EUID" -ne 0 ]; then
  echo "[-] Please run this script with sudo: sudo ./uninstall.sh"
  exit 1
fi

rm -f /usr/local/bin/maghraby
rm -f /usr/local/bin/medit
rm -rf /usr/local/share/maghraby-editor

echo "[✔] The editor has been successfully removed from the system."
