#!/bin/bash
# เพิ่มขั้นตอน build/publish flavor developer ท้าย workflow (รันซ้ำได้)
set -e
f=$(grep -l 'assembleShopRelease' .github/workflows/*.yml 2>/dev/null | head -1)
[ -n "$f" ] || { echo "หยุด: ไม่เจอ workflow ที่มี assembleShopRelease (รันใน ~/nimit-customer หรือเปล่า)"; exit 1; }
if grep -q 'assembleDeveloperRelease' "$f"; then echo "มีขั้นตอน developer อยู่แล้วใน $f ไม่ต้องทำซ้ำ"; exit 0; fi
last=$(grep -v '^[[:space:]]*$' "$f" | tail -n 1)
case "$last" in
  *'shop test'*) ;;
  *) echo "หยุด: ท้ายไฟล์ $f ไม่ใช่ขั้นตอน Publish shop ส่งผล: tail -n 8 $f มาให้ดู"; exit 1;;
esac
ind=$(grep 'assembleShopRelease' "$f" | head -1 | sed 's/-.*//')
[ -n "$(tail -c1 "$f")" ] && echo >> "$f"
{
  printf '%s\n' "$ind- run: gradle assembleDeveloperRelease --no-daemon"
  printf '%s\n' "$ind- name: Publish developer"
  printf '%s\n' "$ind  env:"
  printf '%s\n' "$ind    GH_TOKEN: \${{ secrets.RELEASES_TOKEN }}"
  printf '%s\n' "$ind  run: |"
  printf '%s\n' "$ind    cp app/build/outputs/apk/developer/release/*.apk NimitDeveloper-build-\${{ github.run_number }}.apk"
  printf '%s\n' "$ind    gh release create \"developer-build-\${{ github.run_number }}\" NimitDeveloper-build-\${{ github.run_number }}.apk --repo est1728/NimitDelivery-releases --title \"Developer \${{ github.run_number }}\" --notes \"developer test\" --prerelease"
} >> "$f"
echo "== ตรวจผล: $f =="
grep -n -E 'assemble(Shop|Developer)Release|Publish (shop|developer)|developer-build' "$f"
echo "== 8 บรรทัดท้ายไฟล์ =="
tail -n 8 "$f"
