#!/bin/bash
# ชั่วคราว: หน้าออเดอร์ว่างแสดงตัวเลขวินิจฉัย (ออเดอร์ที่ฟังได้ / ร้านในเขต / รหัสเขตที่ใช้ค้นหา) เพื่อหาว่าติดตรงไหน
# ต้องรัน admin-empty-v1.sh มาก่อน  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
S=$J/ui/AdminOrdersScreen.kt
D=$J/data/AdminOrders.kt
[ -f $S ] && [ -f $D ] || die "ไม่เจอไฟล์หน้าออเดอร์/ข้อมูลออเดอร์"
grep -q 'ไม่ใช่ของร้านในเขตนี้' $S || grep -q 'วินิจฉัย' $S || die "ต้องรัน admin-empty-v1.sh ก่อน"

if grep -q 'zoneIds' $D; then
  echo "AdminOrders.kt: แก้แล้ว ข้าม"
else
  grep -q 'val ready = mutableStateOf(false)' $D || die "ไม่เจอจุดยึด ready"
  grep -q 'if (!ids.contains(session.franchiseId)) ids.add(session.franchiseId)' $D || die "ไม่เจอจุดยึด ids"
  sed -i 's|^\( *\)val ready = mutableStateOf(false)$|\1val ready = mutableStateOf(false)\n\1val zoneIds = mutableStateOf<List<String>>(emptyList())|' $D
  sed -i 's|^\( *\)if (!ids.contains(session.franchiseId)) ids.add(session.franchiseId)$|\1if (!ids.contains(session.franchiseId)) ids.add(session.franchiseId)\n\1zoneIds.value = ids.toList()|' $D
  grep -q 'zoneIds.value' $D || die "แก้ AdminOrders.kt ไม่สำเร็จ"
  echo "AdminOrders.kt: แก้แล้ว"
fi

if grep -q 'วินิจฉัย' $S; then
  echo "AdminOrdersScreen.kt: แก้แล้ว ข้าม"
else
  sed -i '/scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"/c\                        scope.isEmpty() -> "[วินิจฉัย] ไม่พบร้านที่ franchiseIds มีรหัสเขต: " + store.zoneIds.value.joinToString(",") + " | ออเดอร์ที่ฟังได้ " + store.orders.value.size + " ใบ"' $S
  sed -i '/ไม่ใช่ของร้านในเขตนี้/c\                        all.isEmpty() \&\& store.orders.value.isNotEmpty() -> "[วินิจฉัย] ออเดอร์ " + store.orders.value.size + " ใบ (shopId แรก: " + store.orders.value.first().s("shopId") + ") | ร้านในเขต " + scope.size + " ร้าน: " + scope.take(3).joinToString(",")' $S
  sed -i 's|^\( *\)else -> "ไม่มีออเดอร์"$|\1else -> "[วินิจฉัย] ไม่มีออเดอร์ (ฟังได้ " + store.orders.value.size + " ใบ, ร้านในเขต " + scope.size + " ร้าน)"|' $S
  grep -q 'วินิจฉัย' $S || die "แก้ AdminOrdersScreen.kt ไม่สำเร็จ"
  echo "AdminOrdersScreen.kt: แก้แล้ว"
fi
echo "---- ผลตรวจ ----"
grep -n -B3 -A4 'วินิจฉัย' $S | cut -c1-230
for f in $S $D; do o=$(tr -cd '{' < $f | wc -c); c=$(tr -cd '}' < $f | wc -c); p1=$(tr -cd '(' < $f | wc -c); p2=$(tr -cd ')' < $f | wc -c); echo "$f {} $o/$c () $p1/$p2"; done
echo "เสร็จ"
