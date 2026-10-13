#!/bin/bash
# เปลี่ยน flavor developer -> dev ตามเอกสาร nimit-developer-system-prompt.md (รันซ้ำได้)
set -e
G=app/build.gradle.kts
W=$(grep -l 'assembleDeveloperRelease\|assembleDevRelease' .github/workflows/*.yml 2>/dev/null | head -1)
[ -f "$G" ] || { echo "หยุด: ไม่เจอ $G (รันใน ~/nimit-customer หรือเปล่า)"; exit 1; }
if [ -d app/src/developer ] && [ ! -d app/src/dev ]; then
  mv app/src/developer app/src/dev
elif [ -d app/src/developer ] && [ -d app/src/dev ]; then
  echo "หยุด: มีทั้ง app/src/developer และ app/src/dev ตรวจเอง"; exit 1
fi
[ -d app/src/dev ] || { echo "หยุด: ไม่เจอ app/src/dev หรือ app/src/developer"; exit 1; }
sed -i 's|create("developer") { dimension = "app"; applicationId = "com.nimit.delivery.developer" }|create("dev") { dimension = "app"; applicationId = "com.nimit.delivery.dev" }|' "$G"
S=app/src/dev/res/values/strings.xml
[ -f "$S" ] && sed -i 's|<string name="app_name">[^<]*</string>|<string name="app_name">ระบบผู้พัฒนา Nimit Delivery</string>|' "$S"
if [ -n "$W" ]; then
  sed -i -e 's|assembleDeveloperRelease|assembleDevRelease|' \
         -e 's|Publish developer|Publish dev|' \
         -e 's|apk/developer/release|apk/dev/release|' \
         -e 's|NimitDeveloper-build|NimitDev-build|g' \
         -e 's|developer-build-|dev-build-|' \
         -e 's|--title "Developer |--title "Dev |' \
         -e 's|--notes "developer test"|--notes "dev test"|' "$W"
else
  echo "คำเตือน: ไม่เจอขั้นตอน developer ใน workflow (ข้ามส่วน workflow)"
fi
echo "== ตรวจผล =="
grep -n 'create("dev")' "$G" || echo "!! gradle ไม่ถูกแก้"
ls app/src/dev
grep -n 'app_name' "$S"
[ -n "$W" ] && grep -n -E 'DevRelease|Publish dev|dev-build|NimitDev|apk/dev' "$W"
grep -c 'developer' "$G" | sed 's/^/จำนวน developer ที่เหลือใน gradle: /'
