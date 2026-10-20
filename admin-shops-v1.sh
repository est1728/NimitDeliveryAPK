#!/bin/bash
# นับร้านเข้าเขตให้ครบ: นอกจาก shops.franchiseIds (แบบใหม่ หลายเขต) ให้อ่าน shops.franchiseId (ฟิลด์เก่า เขตเดียว) ด้วย
# เอกสารส่งต่อบอกว่าฟิลด์เก่าถูกลบเฉพาะร้านที่เคยย้าย/คัดลอก ร้านเดิมจึงอาจยังใช้ฟิลด์เก่า  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
D=app/src/admin/java/com/nimit/delivery/data/AdminOrders.kt
[ -f $D ] || die "ไม่เจอ $D"
if grep -q 'ร้านแบบฟิลด์เก่า' $D; then
  echo "แก้แล้ว ข้าม"
else
  LN=$(grep -n 'regs.add(db.collection("orders")' $D | head -1 | cut -d: -f1)
  [ -n "$LN" ] || die "ไม่เจอจุดยึด orders listener"
  cat > .admin-shops-block.tmp <<'KT_EOF'
                // ร้านแบบฟิลด์เก่า: shops.franchiseId (เขตเดียว) ยังไม่ถูกย้ายไปเป็น franchiseIds
                var idx2 = 0
                for (chunk in ids.chunked(10)) {
                    val j = 1000 + idx2
                    idx2++
                    regs.add(db.collection("shops").whereIn("franchiseId", chunk).addSnapshotListener { snap, e ->
                        if (e != null) error.value = e.message
                        if (snap != null) {
                            chunkShops[j] = snap.documents.map { it.id }.toSet()
                            shopIds.value = chunkShops.values.flatten().toSet()
                        }
                    })
                }
KT_EOF
  head -n $((LN-1)) $D > .admin-shops-new.tmp
  cat .admin-shops-block.tmp >> .admin-shops-new.tmp
  tail -n +$LN $D >> .admin-shops-new.tmp
  mv .admin-shops-new.tmp $D
  rm -f .admin-shops-block.tmp
  grep -q 'ร้านแบบฟิลด์เก่า' $D || die "แก้ไม่สำเร็จ"
  echo "แก้แล้ว"
fi
echo "---- ผลตรวจ ----"
o=$(tr -cd '{' < $D | wc -c); c=$(tr -cd '}' < $D | wc -c); p1=$(tr -cd '(' < $D | wc -c); p2=$(tr -cd ')' < $D | wc -c)
echo "{} $o/$c  () $p1/$p2"
grep -n 'franchiseId"' $D | cut -c1-160
echo "เสร็จ"
