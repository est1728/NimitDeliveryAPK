#!/bin/bash
# ทุกแถวข้อมูลในหน้ารายละเอียดออเดอร์ใช้รูปแบบ "ชื่อ : ข้อมูล" ชิดซ้าย (รวมส่วนรายละเอียดออเดอร์)  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
F=app/src/admin/java/com/nimit/delivery/ui/AdminOrderDetail.kt
[ -f $F ] || die "ไม่เจอ $F (ต้องรัน admin-detail-v2.sh ก่อน)"
grep -q 'fun DRows' $F || die "ไฟล์ยังเป็นเวอร์ชันเก่า ต้องรัน admin-detail-v2.sh ก่อน"
sed -i 's/DRows(rows, false)/DRows(rows, true)/' $F
sed -i 's/Modifier.width(if (colon) 76.dp else 90.dp)/Modifier.width(if (colon) 84.dp else 90.dp)/' $F
echo "---- ผลตรวจ ----"
grep -c 'DRows(rows, true)' $F | sed 's/^/ส่วนที่ใช้ : ชิดซ้าย: /'
grep -c 'DRows(rows, false)' $F | sed 's/^/ส่วนที่ยังไม่ใช้ : /'
grep -n 'colon) 84.dp' $F | cut -c1-120
echo "เสร็จ"
