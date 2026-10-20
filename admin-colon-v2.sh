#!/bin/bash
# แถวข้อมูลในหน้ารายละเอียดออเดอร์: "ชื่อ : ข้อมูล" ต่อกันชิดซ้าย (เว้นแค่รอบ :) ไม่เว้นคอลัมน์ให้ตรงกัน  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
F=app/src/admin/java/com/nimit/delivery/ui/AdminOrderDetail.kt
[ -f $F ] || die "ไม่เจอ $F"
grep -q 'private fun DRows' $F || die "ไม่เจอ DRows (ต้องรัน admin-detail-v2.sh ก่อน)"
if grep -q 'buildAnnotatedString' $F; then
  echo "แก้แล้ว ข้าม"
else
  LS=$(grep -n '// แถวข้อมูล: colon' $F | head -1 | cut -d: -f1)
  LE=$(grep -n 'private fun DPriceRow' $F | head -1 | cut -d: -f1)
  [ -n "$LS" ] && [ -n "$LE" ] || die "ไม่เจอตำแหน่งของ DRows"
  [ $LE -gt $((LS+2)) ] || die "ตำแหน่ง DRows ผิดปกติ"
  cat > .admin-colon.tmp <<'KT_EOF'
// แถวข้อมูลแบบ "ชื่อ : ข้อมูล" ต่อกันชิดซ้าย (ไม่เว้นคอลัมน์ให้ตรงกัน)
@Composable
private fun DRows(rows: List<DR>, colon: Boolean) {
    for (i in rows.indices) {
        val r = rows[i]
        val txt = buildAnnotatedString {
            withStyle(SpanStyle(color = r.labelColor, fontWeight = FontWeight.Normal)) { append(r.label + "  :  ") }
            withStyle(SpanStyle(color = r.color, fontWeight = FontWeight.Bold)) { append(r.value) }
        }
        Text(
            txt, fontSize = if (r.small) 11.sp else 13.sp, textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
        )
        if (i < rows.size - 1) HorizontalDivider(color = DRowLine)
    }
}

KT_EOF
  head -n $((LS-1)) $F > .admin-colon-new.tmp
  cat .admin-colon.tmp >> .admin-colon-new.tmp
  tail -n +$((LE-1)) $F >> .admin-colon-new.tmp
  mv .admin-colon-new.tmp $F
  rm -f .admin-colon.tmp
  grep -q 'import androidx.compose.ui.text.buildAnnotatedString' $F || sed -i 's|^import androidx.compose.ui.text.font.FontWeight$|import androidx.compose.ui.text.SpanStyle\nimport androidx.compose.ui.text.buildAnnotatedString\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.withStyle|' $F
  grep -q 'buildAnnotatedString {' $F || die "แก้ไม่สำเร็จ"
  echo "แก้แล้ว"
fi
echo "---- ผลตรวจ ----"
o=$(tr -cd '{' < $F | wc -c); c=$(tr -cd '}' < $F | wc -c); p1=$(tr -cd '(' < $F | wc -c); p2=$(tr -cd ')' < $F | wc -c)
echo "{} $o/$c  () $p1/$p2"
grep -n 'import androidx.compose.ui.text' $F | cut -c1-90
grep -c 'private fun DRows' $F | sed 's/^/DRows กี่ตัว: /'
grep -c 'private fun DPriceRow' $F | sed 's/^/DPriceRow กี่ตัว: /'
echo "เสร็จ"
