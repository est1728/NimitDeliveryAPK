#!/bin/bash
# แก้ build แดงฝั่งลูกค้า: RingShopCard.kt ใช้ asFrameworkPaint แต่ขาด import  (ไม่ใช่โค้ดฝั่งแอดมิน แต่ทำให้ build ทั้งโปรเจกต์แดง)
# รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
F=app/src/customer/java/com/nimit/delivery/ui/RingShopCard.kt
[ -f $F ] || die "ไม่เจอ $F"
grep -q 'asFrameworkPaint' $F || die "ไฟล์นี้ไม่มี asFrameworkPaint (ต้องดู error ใหม่)"
IMP='import androidx.compose.ui.graphics.asFrameworkPaint'
if grep -q "^$IMP\$" $F || grep -q '^import androidx.compose.ui.graphics.\*$' $F; then
  echo "มี import อยู่แล้ว ไม่แก้ (error นี้อาจมาจากสาเหตุอื่น ส่งบรรทัด e: มาให้ผม)"
else
  LN=$(grep -n '^import ' $F | head -1 | cut -d: -f1)
  [ -n "$LN" ] || die "ไม่เจอบรรทัด import"
  head -n $((LN-1)) $F > .rs.tmp
  echo "$IMP" >> .rs.tmp
  tail -n +$LN $F >> .rs.tmp
  mv .rs.tmp $F
  grep -q "^$IMP\$" $F || die "เพิ่ม import ไม่สำเร็จ"
  echo "เพิ่ม import แล้ว"
fi
echo "---- ผลตรวจ ----"
grep -n 'asFrameworkPaint' $F | cut -c1-140
echo "เสร็จ"
