#!/bin/bash
# หน้าออเดอร์ว่าง: บอกสาเหตุให้ชัด (กำลังโหลด / มีออเดอร์แต่ไม่ใช่ร้านในเขตนี้ / ไม่มีออเดอร์จริง)  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
S=app/src/admin/java/com/nimit/delivery/ui/AdminOrdersScreen.kt
[ -f $S ] || die "ไม่เจอ $S"
if grep -q 'ไม่ใช่ของร้านในเขตนี้' $S; then
  echo "แก้แล้ว ข้าม"
else
  grep -q 'store.ready.value && scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"' $S || die "ไม่เจอจุดยึดข้อความร้านค้า"
  grep -q 'else -> "ไม่มีออเดอร์"' $S || die "ไม่เจอจุดยึด else"
  sed -i 's|^\( *\)store.ready.value \&\& scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"$|\1!store.ready.value -> "กำลังโหลดข้อมูลเขต..."\n\1scope.isEmpty() -> "ยังไม่มีร้านค้าในเขตนี้"|' $S
  sed -i 's|^\( *\)else -> "ไม่มีออเดอร์"$|\1all.isEmpty() \&\& store.orders.value.isNotEmpty() -> "มีออเดอร์ " + store.orders.value.size + " รายการในระบบ แต่ไม่ใช่ของร้านในเขตนี้ (เขตนี้มี " + scope.size + " ร้าน)"\n\1else -> "ไม่มีออเดอร์"|' $S
  grep -q 'ไม่ใช่ของร้านในเขตนี้' $S || die "แก้ไม่สำเร็จ"
  echo "แก้แล้ว"
fi
echo "---- ผลตรวจ ----"
grep -n -B3 -A4 'ไม่ใช่ของร้านในเขตนี้' $S | cut -c1-200
o=$(tr -cd '{' < $S | wc -c); c=$(tr -cd '}' < $S | wc -c); echo "{} $o/$c"
echo "เสร็จ"
