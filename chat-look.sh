#!/bin/bash
# รันใน ~/nimit-customer โดยมี ChatScreen.new.kt และ ChatPhotoProvider.kt อยู่ในโฟลเดอร์เดียวกัน
# แทนที่ ChatScreen ตัวร่วม (customer/rider/admin ใช้ร่วมกัน) ด้วยเวอร์ชันที่หน้าตาตรงกับ chat.html
# signature เดิม: ChatScreen(orderId: String, role: String, onBack: () -> Unit)  — สำรองไฟล์เดิมไว้ที่ chat-backup/
set -e
NEW=ChatScreen.new.kt; PROV=ChatPhotoProvider.kt
[ -f "$NEW" ] && [ -f "$PROV" ] || { echo "!! ต้องมี $NEW และ $PROV ในโฟลเดอร์นี้"; exit 1; }
OLD=$(find app/src -name ChatScreen.kt -not -path '*/rider/*' 2>/dev/null | head -1)
[ -n "$OLD" ] || { echo "!! หา ChatScreen.kt ไม่เจอ"; exit 1; }
DIR=$(dirname "$OLD")
echo "ไฟล์เดิม: $OLD"
# ถ้าไฟล์เดิมมีของสาธารณะอื่นที่ไฟล์อื่นอาจใช้อยู่ ให้หยุดก่อน
EXTRA=$(grep -nE '^(fun|class|object|data class|enum class|interface|val|const val|var) ' "$OLD" | grep -v 'fun ChatScreen(' || true)
if [ -n "$EXTRA" ] && [ "$FORCE" != "1" ]; then
  echo "!! ไฟล์เดิมมีของสาธารณะอื่นนอกจาก ChatScreen (ไฟล์อื่นอาจเรียกใช้):"; echo "$EXTRA"
  echo "   ส่งข้อความนี้มาให้ผม หรือถ้าแน่ใจให้รัน: FORCE=1 bash chat-look.sh"; exit 1
fi
echo "--- จุดที่เรียก ChatScreen (ต้องเป็น 3 ค่า: orderId, role, onBack):"
grep -rn 'ChatScreen(' app/src --include=*.kt | grep -v "^$OLD:" | cut -c1-170 || true
mkdir -p chat-backup
[ -f chat-backup/ChatScreen.kt.bak ] || cp "$OLD" chat-backup/ChatScreen.kt.bak
cp "$NEW" "$OLD"
cp "$PROV" "$DIR/ChatPhotoProvider.kt"
# xml ของ FileProvider (ถ่ายรูปในแชท)
mkdir -p app/src/main/res/xml
[ -f app/src/main/res/xml/chat_photo_paths.xml ] || cat > app/src/main/res/xml/chat_photo_paths.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="chat_photos" path="chat_photos/" />
</paths>
XML
MF=app/src/main/AndroidManifest.xml
[ -f "$MF" ] || { echo "!! ไม่พบ $MF"; exit 1; }
grep -q '</application>' "$MF" || { echo "!! $MF ไม่มี </application>"; exit 1; }
grep -q 'ChatPhotoProvider' "$MF" || sed -i 's#</application>#        <provider android:name="com.nimit.delivery.ui.ChatPhotoProvider" android:authorities="${applicationId}.chatphotos" android:exported="false" android:grantUriPermissions="true">\n            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/chat_photo_paths" />\n        </provider>\n    </application>#' "$MF"
grep -rq 'RECORD_AUDIO' app/src --include=AndroidManifest.xml || sed -i 's#<application#<uses-permission android:name="android.permission.RECORD_AUDIO" />\n    <application#' "$MF"
echo "--- ตรวจผล"; grep -n 'ChatPhotoProvider\|RECORD_AUDIO' "$MF" | cut -c1-120; ls "$DIR" | grep -i 'chat'
