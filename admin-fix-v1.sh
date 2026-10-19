#!/bin/bash
# แก้ build แดง: ธีมแอปหลักอ้าง @mipmap/ic_launcher_foreground (ไอคอน splash) แต่ flavor admin ยังไม่มี
set -u
die() { echo "หยุด: $1"; exit 1; }
A=app/src/admin/res
[ -d $A/drawable ] || die "ไม่เจอ $A/drawable (ต้องรัน admin-setup-v1.sh ก่อน)"
[ -f $A/drawable/admin_ic_fg.xml ] || die "ไม่เจอ admin_ic_fg.xml"
[ -f $A/drawable/admin_ic_bg.xml ] || die "ไม่เจอ admin_ic_bg.xml"
mkdir -p $A/mipmap-anydpi
cp $A/drawable/admin_ic_fg.xml $A/mipmap-anydpi/ic_launcher_foreground.xml
cp $A/drawable/admin_ic_bg.xml $A/mipmap-anydpi/ic_launcher_background.xml
echo "---- ผลตรวจ ----"
ls $A/mipmap-anydpi
grep -c 'vector' $A/mipmap-anydpi/ic_launcher_foreground.xml $A/mipmap-anydpi/ic_launcher_background.xml
echo "เสร็จ"
