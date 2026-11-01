#!/bin/bash
# แก้ build แดงของ dev: DevNotifyCat.kt บรรทัด "Int + String" (ต้องแปลงเป็นข้อความก่อน) + ปรับป้ายคอลัมน์การเงิน (รันซ้ำได้)
set -e
D=app/src/dev/java/com/nimit/delivery
C="$D/ui/DevNotifyCat.kt"
[ -f "$C" ] || { echo "หยุด: ไม่เจอ $C (ต้องรัน dev-ncat.sh ก่อน และรันใน ~/nimit-customer)"; exit 1; }
if grep -q 'else p.items.size + " ข้อความ รวม "' "$C"; then
  sed -i 's/else p\.items\.size + " ข้อความ รวม "/else p.items.size.toString() + " ข้อความ รวม "/' "$C"
  echo "แก้ DevNotifyCat.kt แล้ว"
elif grep -q 'p.items.size.toString() + " ข้อความ รวม "' "$C"; then
  echo "DevNotifyCat.kt แก้ไว้แล้ว"
else
  echo "หยุด: ไม่เจอบรรทัดที่ต้องแก้ใน $C (ส่งผล grep -n 'ข้อความ รวม' $C มาให้ดู)"; exit 1
fi
F="$D/ui/DevFinance.kt"
if [ -f "$F" ]; then
  if grep -q '"walletCut" to 90.dp' "$F"; then
    sed -i 's/"walletCut" to 90\.dp/"หักเครดิตไรเดอร์ (walletCut)" to 130.dp/' "$F"
    sed -i 's/"ชื่อคอลัมน์ตรงกับฟิลด์ในออเดอร์ อ่านอย่างเดียว"/"รายได้แอดมิน = หักเครดิตไรเดอร์ (walletCut) ไม่นับการเติมเครดิต (คำนวณจากออเดอร์เท่านั้น) ชื่อคอลัมน์ตรงกับฟิลด์ในออเดอร์ อ่านอย่างเดียว"/' "$F"
    echo "ปรับป้ายคอลัมน์การเงินแล้ว"
  else
    echo "ป้ายคอลัมน์การเงินปรับไว้แล้วหรือยังไม่มีหน้าการเงิน (ข้าม)"
  fi
else
  echo "ยังไม่มีหน้าการเงิน (ยังไม่ได้รัน dev-hubfin.sh) ข้ามส่วนนี้"
fi
echo "== ตรวจผล =="
grep -n 'items.size.toString()' "$C"
[ -f "$F" ] && grep -n 'walletCut' "$F" | head -3
git status --short
