#!/bin/bash
# ใส่ตัวอัปเดตในแอป (UpdatePrompt) ให้แอป dev แบบเดียวกับ shop/rider (รันซ้ำได้)
set -e
F=app/src/dev/java/com/nimit/delivery/ui/DevNav.kt
[ -f "$F" ] || { echo "หยุด: ไม่เจอ $F (รันใน ~/nimit-customer หรือเปล่า)"; exit 1; }
if grep -q 'UpdatePrompt(' "$F"; then echo "มี UpdatePrompt อยู่แล้ว ไม่ต้องทำซ้ำ"; exit 0; fi
grep -q '^fun DevNav() {$' "$F" || { echo "หยุด: ไม่เจอบรรทัด fun DevNav() { ใน $F"; exit 1; }
sed -i '/^fun DevNav() {$/a\    UpdatePrompt(appName = "Nimit Developer", tagPrefix = "dev-build-")' "$F"
echo "== ตรวจผล =="
grep -n -B1 -A1 'UpdatePrompt(' "$F"
