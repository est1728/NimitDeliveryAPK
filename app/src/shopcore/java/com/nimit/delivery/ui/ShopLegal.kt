package com.nimit.delivery.ui

/** เนื้อหา "เงื่อนไขการใช้บริการ" และ "นโยบายข้อมูลส่วนบุคคล" ของแอป Nimit ร้านค้า (ไทย + อังกฤษ) แอปอื่นทำไฟล์แบบเดียวกันของตัวเอง */
object ShopLegal {
    const val VERSION: String = "1.2"

    val TERMS: List<LegalSection> = listOf(
        LegalSection("1. ขอบเขตของบริการ", listOf(
            "แอป Nimit ร้านค้า เป็นเครื่องมือให้ร้านค้าที่ลงทะเบียนกับ Nimit Delivery ใช้รับและจัดการออเดอร์ เมนูอาหาร โปรโมชัน และแต้มสะสมของลูกค้า",
            "บริการนี้สำหรับร้านค้าที่ได้รับการลงทะเบียนจากผู้ดูแลระบบเท่านั้น และสำหรับผู้ใช้ที่บรรลุนิติภาวะแล้ว"
        )),
        LegalSection("2. บัญชีและการเข้าสู่ระบบ", listOf(
            "เข้าสู่ระบบด้วยเบอร์โทรของร้านที่ลงทะเบียนไว้ และยืนยันด้วยรหัส OTP",
            "ร้านต้องดูแลความปลอดภัยของเบอร์โทรและอุปกรณ์ของตน และรับผิดชอบการใช้งานทั้งหมดที่เกิดจากบัญชีของร้าน"
        )),
        LegalSection("3. หน้าที่ของร้านค้า", listOf(
            "ข้อมูลร้าน เมนู ราคา และรูปภาพที่ใส่ในระบบต้องถูกต้อง และไม่ละเมิดสิทธิของผู้อื่น",
            "รับหรือปฏิเสธออเดอร์ อัปเดตสถานะ และเตรียมอาหารตามที่รับออเดอร์ไว้",
            "เปิด-ปิดร้านให้ตรงกับความจริง",
            "ห้ามใช้ระบบเพื่อการทุจริต สร้างออเดอร์ปลอม หรือรบกวนการทำงานของระบบ"
        )),
        LegalSection("4. ค่าบริการและการรับเงิน", listOf(
            "ค่าธรรมเนียม ค่าจัดส่ง และเงื่อนไขการรับเงิน เป็นไปตามที่ร้านตกลงไว้กับผู้ดูแลระบบ"
        )),
        LegalSection("5. การแจ้งเตือนออเดอร์", listOf(
            "แอปส่งการแจ้งเตือนและเสียงเมื่อมีออเดอร์ใหม่ และอาจทำงานเบื้องหลังเพื่อรับออเดอร์ ร้านควรอนุญาตการแจ้งเตือนและไม่ปิดการทำงานของแอปในเครื่อง",
            "การแจ้งเตือนขึ้นอยู่กับอินเทอร์เน็ตและตัวเครื่อง เราไม่สามารถรับประกันว่าจะถึงทุกครั้ง ร้านควรเปิดแอปตรวจสอบออเดอร์ในช่วงเวลาทำการ"
        )),
        LegalSection("6. การระงับและยกเลิกบริการ", listOf(
            "ผู้ดูแลระบบอาจระงับหรือยกเลิกบัญชีที่ฝ่าฝืนเงื่อนไข หรือมีพฤติกรรมเสี่ยงต่อการทุจริต",
            "ร้านสามารถขอลบบัญชีได้ในแอป (เมนู > ขอลบบัญชีร้าน) หรือติดต่อผู้ดูแลระบบ ผู้ดูแลระบบจะติดต่อกลับเพื่อยืนยันตัวตนก่อนดำเนินการ"
        )),
        LegalSection("7. ระบบและการเก็บข้อมูล", listOf(
            "ข้อมูลที่ร้านและลูกค้าใส่ในระบบ ถูกเก็บไว้ในระบบของผู้พัฒนา คือฐานข้อมูลของ Nimit Delivery ซึ่งทำงานบนโครงสร้างพื้นฐานคลาวด์ของ Google Firebase",
            "การเก็บ ใช้ และเปิดเผยข้อมูลส่วนบุคคลเป็นไปตามนโยบายข้อมูลส่วนบุคคลของเรา"
        )),
        LegalSection("8. ข้อจำกัดความรับผิด", listOf(
            "บริการให้ตามสภาพที่เป็นอยู่ เราไม่รับผิดชอบต่อความเสียหายจากเครือข่ายขัดข้อง เหตุสุดวิสัย หรือการใช้งานที่ผิดไปจากเงื่อนไข ภายใต้ขอบเขตที่กฎหมายอนุญาต"
        )),
        LegalSection("9. กฎหมายที่ใช้บังคับ", listOf(
            "เงื่อนไขนี้อยู่ภายใต้กฎหมายแห่งราชอาณาจักรไทย"
        )),
        LegalSection("10. การเปลี่ยนแปลงเงื่อนไข", listOf(
            "เราอาจปรับปรุงเงื่อนไขนี้เป็นครั้งคราว โดยแสดงเลขฉบับที่ในหน้านี้ การใช้งานต่อถือว่าคุณยอมรับฉบับที่ปรับปรุงแล้ว"
        )),
        LegalSection("11. ผู้ให้บริการและช่องทางติดต่อ", listOf(
            "ผู้ให้บริการ: Nimit - แพลตฟอร์มสั่งอาหาร",
            "อีเมล: aittinon417@gmail.com",
            "LINE ผู้ดูแลระบบ: https://lin.ee/6rWI6rH"
        ))
    )

    val TERMS_EN: List<LegalSection> = listOf(
        LegalSection("1. Scope of service", listOf(
            "The Nimit Shop app is a tool for shops registered with Nimit Delivery to receive and manage orders, menus, promotions and customer loyalty points.",
            "The service is only for shops registered by the administrator, and for users who are of legal age."
        )),
        LegalSection("2. Account and sign-in", listOf(
            "Sign in with the registered shop phone number and confirm with a one-time password (OTP).",
            "The shop must keep its phone number and device secure and is responsible for all activity under its account."
        )),
        LegalSection("3. Shop responsibilities", listOf(
            "Shop details, menus, prices and photos must be accurate and must not infringe the rights of others.",
            "Accept or decline orders, update order status, and prepare the food for the orders you accept.",
            "Keep the open/closed status truthful.",
            "Do not use the system for fraud, fake orders, or to disrupt the system."
        )),
        LegalSection("4. Fees and payment", listOf(
            "Fees, delivery charges and payment terms are as agreed between the shop and the administrator."
        )),
        LegalSection("5. Order notifications", listOf(
            "The app sends notifications and sounds for new orders and may run in the background to receive orders. Please allow notifications and do not disable the app on your device.",
            "Notifications depend on the internet connection and your device, so we cannot guarantee delivery every time. Please open the app to check orders during business hours."
        )),
        LegalSection("6. Suspension and termination", listOf(
            "The administrator may suspend or terminate accounts that violate these terms or show a risk of fraud.",
            "A shop can request account deletion in the app (Menu > Request shop account deletion) or by contacting the administrator. The administrator will contact you to verify your identity before proceeding."
        )),
        LegalSection("7. System and data storage", listOf(
            "Data entered by shops and customers is stored in the developer's system, namely the Nimit Delivery database, which runs on Google Firebase cloud infrastructure.",
            "Personal data is collected, used and disclosed in accordance with our Privacy Policy."
        )),
        LegalSection("8. Limitation of liability", listOf(
            "The service is provided as is. To the extent permitted by law, we are not liable for damage caused by network failures, force majeure, or use that violates these terms."
        )),
        LegalSection("9. Governing law", listOf(
            "These terms are governed by the laws of the Kingdom of Thailand."
        )),
        LegalSection("10. Changes to these terms", listOf(
            "We may update these terms from time to time and show the version number on this page. Continued use means you accept the updated version."
        )),
        LegalSection("11. Operator and contact", listOf(
            "Operator: Nimit - Food Ordering Platform (Nimit Delivery)",
            "Email: aittinon417@gmail.com",
            "Admin LINE: https://lin.ee/6rWI6rH"
        ))
    )

    val PRIVACY: List<LegalSection> = listOf(
        LegalSection("1. ผู้ควบคุมข้อมูลส่วนบุคคล", listOf(
            "ผู้ให้บริการและผู้ควบคุมข้อมูล: Nimit - แพลตฟอร์มสั่งอาหาร",
            "อีเมลติดต่อ: aittinon417@gmail.com",
            "LINE ผู้ดูแลระบบ: https://lin.ee/6rWI6rH",
            "ข้อมูลทั้งหมดถูกเก็บไว้ในระบบของผู้พัฒนา คือฐานข้อมูลของ Nimit Delivery บนโครงสร้างพื้นฐานคลาวด์ของ Google Firebase"
        )),
        LegalSection("2. ข้อมูลที่เราเก็บ", listOf(
            "ข้อมูลร้าน: ชื่อร้าน เบอร์โทร ที่อยู่ เวลาทำการ และพิกัดร้านที่คุณปักหมุด",
            "ข้อมูลที่ร้านใส่ในระบบ: เมนู ราคา รูปภาพ โปรโมชัน และข้อมูลแต้มสะสม",
            "ข้อมูลออเดอร์: รายการอาหาร ยอดเงิน สถานะ รวมถึงชื่อ เบอร์โทร และที่อยู่ของลูกค้าที่สั่งกับร้านของคุณ",
            "ข้อมูลเทคนิค: รหัสอุปกรณ์สำหรับส่งการแจ้งเตือน ประเภทอุปกรณ์ และข้อมูลการเข้าใช้งานระบบ (log)",
            "ตำแหน่ง: ใช้เฉพาะเมื่อคุณกดใช้ตำแหน่งปัจจุบันเพื่อปักหมุดร้าน เราไม่ติดตามตำแหน่งของคุณเบื้องหลัง"
        )),
        LegalSection("3. เราใช้ข้อมูลทำอะไร และฐานทางกฎหมาย", listOf(
            "ยืนยันตัวตนและให้คุณเข้าสู่ระบบ (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "แสดงร้านและเมนูให้ลูกค้า และรับออเดอร์ของร้านคุณ (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "ส่งการแจ้งเตือนเมื่อมีออเดอร์ใหม่ (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "ติดต่อกลับเมื่อมีปัญหาเกี่ยวกับออเดอร์หรือบัญชี และป้องกันการทุจริต (เพื่อประโยชน์โดยชอบด้วยกฎหมาย)",
            "ใช้ตำแหน่งปัจจุบันเพื่อปักหมุดร้าน (โดยความยินยอมของคุณ ซึ่งถอนได้ที่การตั้งค่าเครื่อง)",
            "เก็บข้อมูลธุรกรรมและบัญชีตามที่กฎหมายกำหนด (เพื่อปฏิบัติตามกฎหมาย)"
        )),
        LegalSection("4. เราเปิดเผยข้อมูลให้ใครบ้าง", listOf(
            "ลูกค้า: ชื่อร้าน เมนู ที่ตั้ง และเวลาทำการ",
            "ไรเดอร์ที่รับงาน: ที่อยู่และเบอร์โทรของร้าน เพื่อมารับอาหาร",
            "ผู้ดูแลระบบ Nimit Delivery",
            "ผู้ให้บริการที่ช่วยให้ระบบทำงาน ได้แก่ Google Firebase (ยืนยันตัวตนด้วย OTP ฐานข้อมูล และการแจ้งเตือน), Cloudinary (เก็บรูปภาพ), Vercel (ส่งการแจ้งเตือน) และผู้ให้บริการแผนที่ (Esri / OpenStreetMap)",
            "หน่วยงานรัฐเมื่อมีกฎหมายหรือคำสั่งที่มีอำนาจกำหนด",
            "เราไม่ขายข้อมูลของคุณ"
        )),
        LegalSection("5. ที่เก็บข้อมูลและการส่งไปต่างประเทศ", listOf(
            "ระบบและฐานข้อมูลทำงานบนคลาวด์ของผู้ให้บริการ ได้แก่ Google Cloud / Firebase (Cloud Firestore เป็นฐานข้อมูลหลักเก็บข้อมูลร้านค้า บัญชีผู้ใช้ และประวัติรายการต่างๆ), Cloudinary (จัดเก็บและจัดการรูปภาพ) และ Vercel (โฮสต์เว็บไซต์และบริการส่งการแจ้งเตือน)",
            "ข้อมูลอาจถูกจัดเก็บและสำรองในศูนย์ข้อมูลของผู้ให้บริการที่ตั้งอยู่ต่างประเทศ",
            "เราเลือกผู้ให้บริการที่มีมาตรฐานการคุ้มครองข้อมูลในระดับที่เหมาะสม ข้อมูลถูกเข้ารหัสระหว่างส่งและขณะจัดเก็บ และผู้ให้บริการอยู่ภายใต้ข้อตกลงการประมวลผลข้อมูลที่ผูกพันให้คุ้มครองข้อมูลของคุณ"
        )),
        LegalSection("6. ระยะเวลาเก็บข้อมูล", listOf(
            "ข้อมูลร้าน เมนู และรูปภาพ: เก็บตราบที่บัญชียังใช้งาน และลบภายใน 30 วัน หลังยืนยันคำขอลบบัญชีสำเร็จ",
            "ข้อมูลออเดอร์และธุรกรรม: เก็บไว้ 5 ปี ตามที่กฎหมายการบัญชีกำหนด แล้วลบหรือทำให้ไม่สามารถระบุตัวตนได้",
            "ข้อมูลการเข้าใช้งานระบบ (log): เก็บตามที่กฎหมายว่าด้วยการกระทำความผิดเกี่ยวกับคอมพิวเตอร์กำหนด (ไม่น้อยกว่า 90 วัน)",
            "โทเคนแจ้งเตือน: ลบเมื่อออกจากระบบหรือลบบัญชี"
        )),
        LegalSection("7. สิทธิ์ของคุณตาม พ.ร.บ.คุ้มครองข้อมูลส่วนบุคคล พ.ศ. 2562", listOf(
            "ขอเข้าถึงและขอรับสำเนาข้อมูลของคุณ รวมถึงขอให้โอนย้ายข้อมูล",
            "ขอแก้ไขข้อมูลให้ถูกต้อง ขอให้ลบ ทำลาย หรือทำให้ไม่สามารถระบุตัวตนได้",
            "คัดค้านหรือขอให้ระงับการใช้ข้อมูล และถอนความยินยอมได้ทุกเมื่อ (เช่น ปิดสิทธิ์ตำแหน่งที่การตั้งค่าเครื่อง)",
            "ช่องทางใช้สิทธิ: ส่งคำขอทางอีเมล aittinon417@gmail.com หรือทาง LINE ผู้ดูแลระบบ เราจะดำเนินการภายใน 30 วันนับจากยืนยันตัวตนสำเร็จ",
            "ขอลบบัญชีได้ในแอป (เมนู > ขอลบบัญชีร้าน) หรือที่หน้า /legal/shop-delete.html หรือติดต่อ aittinon417@gmail.com ยกเว้นข้อมูลที่กฎหมายกำหนดให้เก็บ",
            "หากเห็นว่าเราไม่ปฏิบัติตามกฎหมาย คุณมีสิทธิร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล (สคส.)"
        )),
        LegalSection("8. ความปลอดภัย", listOf(
            "ข้อมูลส่งผ่านการเชื่อมต่อที่เข้ารหัส และจำกัดการเข้าถึงเฉพาะผู้ที่เกี่ยวข้อง แต่ไม่มีระบบใดปลอดภัย 100% กรุณารักษาเบอร์โทรและอุปกรณ์ของคุณให้ปลอดภัย"
        )),
        LegalSection("9. ข้อมูลของเด็ก", listOf(
            "แอปนี้สำหรับเจ้าของร้านที่บรรลุนิติภาวะแล้ว (อายุตั้งแต่ 20 ปี หรือบรรลุนิติภาวะตามกฎหมาย) เราไม่ตั้งใจเก็บข้อมูลของผู้เยาว์",
            "หากพบว่ามีข้อมูลของผู้เยาว์ เราจะลบข้อมูลนั้นโดยเร็ว หรือขอความยินยอมจากผู้ปกครองตามที่กฎหมายกำหนดก่อนเก็บต่อ"
        )),
        LegalSection("10. การเปลี่ยนแปลงนโยบาย", listOf(
            "เราอาจปรับปรุงนโยบายนี้เป็นครั้งคราว โดยแสดงเลขฉบับที่ในหน้านี้"
        ))
    )

    val PRIVACY_EN: List<LegalSection> = listOf(
        LegalSection("1. Data controller", listOf(
            "Operator and data controller: Nimit - Food Ordering Platform (Nimit Delivery)",
            "Contact email: aittinon417@gmail.com",
            "Admin LINE: https://lin.ee/6rWI6rH",
            "All data is stored in the developer's system, namely the Nimit Delivery database on Google Firebase cloud infrastructure."
        )),
        LegalSection("2. Data we collect", listOf(
            "Shop data: shop name, phone number, address, opening hours, and the shop location you pin.",
            "Data the shop enters: menus, prices, photos, promotions and loyalty-point data.",
            "Order data: items, amounts, status, and the name, phone number and address of customers who order from your shop.",
            "Technical data: device token for notifications, device type, and system access logs.",
            "Location: used only when you tap to use your current location to pin the shop. We do not track your location in the background."
        )),
        LegalSection("3. How we use data and legal bases", listOf(
            "Verify your identity and let you sign in (to perform the service contract).",
            "Show your shop and menu to customers and receive your orders (to perform the service contract).",
            "Send notifications for new orders (to perform the service contract).",
            "Contact you about order or account problems and prevent fraud (legitimate interests).",
            "Use your current location to pin the shop (with your consent, which you can withdraw in device settings).",
            "Keep transaction and account records as required by law (legal obligation)."
        )),
        LegalSection("4. Who we share data with", listOf(
            "Customers: shop name, menu, location and opening hours.",
            "Riders who take a job: the shop's address and phone number, to pick up the food.",
            "The Nimit Delivery administrator.",
            "Service providers that make the system work: Google Firebase (OTP sign-in, database, notifications), Cloudinary (image storage), Vercel (sending notifications) and map providers (Esri / OpenStreetMap).",
            "Government authorities when required by law or a lawful order.",
            "We do not sell your data."
        )),
        LegalSection("5. Where data is stored and international transfers", listOf(
            "The system and database run on cloud providers: Google Cloud / Firebase (Cloud Firestore is the main database for shop data, user accounts and transaction history), Cloudinary (image storage and management) and Vercel (website hosting and the notification service).",
            "Data may be stored and backed up in the providers' data centers located outside Thailand.",
            "We choose providers with appropriate data-protection standards; data is encrypted in transit and at rest, and the providers are bound by data processing terms to protect your data."
        )),
        LegalSection("6. Retention", listOf(
            "Shop, menu and photo data: kept while the account is active and deleted within 30 days after an account deletion request is verified.",
            "Order and transaction data: kept for 5 years as required by accounting law, then deleted or anonymized.",
            "System access logs: kept as required by the Computer Crime Act (at least 90 days).",
            "Notification tokens: deleted on sign-out or account deletion."
        )),
        LegalSection("7. Your rights under Thailand's Personal Data Protection Act B.E. 2562 (2019)", listOf(
            "Access and obtain a copy of your data, and request data portability.",
            "Correct inaccurate data, and request deletion, destruction or anonymization.",
            "Object to or restrict the use of your data, and withdraw consent at any time (for example, turn off location permission in device settings).",
            "How to exercise your rights: email aittinon417@gmail.com or message the administrator on LINE. We will act within 30 days after your identity is verified.",
            "Request account deletion in the app (Menu > Request shop account deletion), on the page /legal/shop-delete.html, or by emailing aittinon417@gmail.com, except for data we are required by law to keep.",
            "If you believe we do not comply with the law, you may complain to the Personal Data Protection Committee Office (PDPC)."
        )),
        LegalSection("8. Security", listOf(
            "Data is sent over encrypted connections and access is limited to relevant people, but no system is 100% secure. Please keep your phone number and device safe."
        )),
        LegalSection("9. Children's data", listOf(
            "This app is for shop owners who are of legal age (20 or older, or legally an adult). We do not knowingly collect minors' data.",
            "If we find any minor's data, we will delete it promptly or obtain guardian consent as required by law before keeping it."
        )),
        LegalSection("10. Changes to this policy", listOf(
            "We may update this policy from time to time and show the version number on this page."
        ))
    )
}
