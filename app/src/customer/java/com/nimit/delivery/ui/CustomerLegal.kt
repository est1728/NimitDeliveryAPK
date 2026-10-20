package com.nimit.delivery.ui

/** เนื้อหา "เงื่อนไขการใช้บริการ" และ "นโยบายข้อมูลส่วนบุคคล" ของแอป Nimit Delivery ลูกค้า (ไทย + อังกฤษ) */
object CustomerLegal {
    const val VERSION: String = "1.0"

    val TERMS: List<LegalSection> = listOf(
        LegalSection("1. ขอบเขตของบริการ", listOf(
            "แอป Nimit Delivery เป็นแพลตฟอร์มสั่งอาหารจากร้านค้าที่ลงทะเบียนกับเรา ให้คุณสั่งอาหาร ติดตามออเดอร์ สะสมแต้ม ใช้คูปอง และใช้ฟีเจอร์โซเชียลกับเกมภายในแอป",
            "สำหรับผู้ใช้ที่บรรลุนิติภาวะ หากอายุต่ำกว่า 20 ปี ควรได้รับความยินยอมจากผู้ปกครอง"
        )),
        LegalSection("2. บัญชีและการเข้าสู่ระบบ", listOf(
            "เข้าสู่ระบบด้วยเบอร์โทรศัพท์ และยืนยันด้วยรหัส OTP เมื่อระบบเปิดใช้",
            "คุณต้องให้ข้อมูลที่ถูกต้อง (ชื่อ เบอร์โทร ที่อยู่จัดส่ง) และดูแลความปลอดภัยของเบอร์โทรและอุปกรณ์ของคุณ"
        )),
        LegalSection("3. การสั่งอาหารและการชำระเงิน", listOf(
            "ออเดอร์เกิดขึ้นเมื่อคุณยืนยันการสั่งซื้อ ร้านและไรเดอร์เป็นผู้เตรียมและจัดส่งอาหาร",
            "ราคา ค่าจัดส่ง และวิธีชำระเงินเป็นไปตามที่แสดงในหน้าชำระเงินก่อนยืนยัน",
            "การยกเลิก การคืนเงิน และการแก้ปัญหาออเดอร์ ให้ติดต่อผู้ดูแลระบบทาง LINE หรืออีเมลด้านล่าง"
        )),
        LegalSection("4. แต้มสะสมและคูปอง", listOf(
            "แต้มสะสมและคูปองจากการสั่งอาหารไม่ใช่เงิน ไม่สามารถแลกเป็นเงินสดได้ และเป็นไปตามเงื่อนไขของแต่ละร้านหรือโปรโมชัน",
            "เราอาจปฏิเสธหรือเพิกถอนคูปองที่ได้มาโดยทุจริต เช่น การสร้างบัญชีใหม่เพื่อรับสิทธิ์ซ้ำ"
        )),
        LegalSection("5. ฟีเจอร์โซเชียล", listOf(
            "การใช้งานโซเชียลต้องสร้างบัญชีและยอมรับนโยบายนี้ก่อน",
            "ห้ามโพสต์เนื้อหาผิดกฎหมาย หยาบคาย ล่วงละเมิด คุกคาม เกลียดชัง ลามกอนาจาร หลอกลวง หรือละเมิดสิทธิของผู้อื่น",
            "คุณรายงานเนื้อหาหรือผู้ใช้ที่ไม่เหมาะสม บล็อกผู้ใช้ และลบเนื้อหาของตนเองได้ เราอาจลบเนื้อหาหรือระงับบัญชีที่ฝ่าฝืน"
        )),
        LegalSection("6. เกมในแอป", listOf(
            "เกมเล่นฟรี เพื่อความบันเทิงระหว่างรอออเดอร์ ไม่มีการเดิมพันหรือเติมเงินจริง",
            "แต้มและไอเท็มในเกมใช้ได้เฉพาะในเกม ไม่สามารถแลกเป็นเงินหรือสินค้า และแยกจากแต้มสะสมจากการสั่งอาหาร"
        )),
        LegalSection("7. การระงับและลบบัญชี", listOf(
            "เราอาจระงับบัญชีที่ฝ่าฝืนเงื่อนไขหรือมีพฤติกรรมทุจริต",
            "คุณขอลบบัญชีและข้อมูลได้ที่อีเมลหรือ LINE ผู้ดูแลระบบด้านล่าง (ดูรายละเอียดในนโยบายข้อมูลส่วนบุคคล)"
        )),
        LegalSection("8. ข้อจำกัดความรับผิด", listOf(
            "บริการให้ตามสภาพที่เป็นอยู่ เราไม่รับผิดชอบต่อความเสียหายจากเครือข่ายขัดข้อง เหตุสุดวิสัย หรือการใช้งานที่ผิดเงื่อนไข ภายใต้ขอบเขตที่กฎหมายอนุญาต ร้านค้าเป็นผู้รับผิดชอบคุณภาพอาหารของตน"
        )),
        LegalSection("9. กฎหมายที่ใช้บังคับและการเปลี่ยนแปลง", listOf(
            "เงื่อนไขนี้อยู่ภายใต้กฎหมายแห่งราชอาณาจักรไทย",
            "เราอาจปรับปรุงเงื่อนไขนี้เป็นครั้งคราว โดยแสดงเลขฉบับที่ในหน้านี้ การใช้งานต่อถือว่ายอมรับฉบับที่ปรับปรุงแล้ว"
        )),
        LegalSection("10. ผู้ให้บริการและช่องทางติดต่อ", listOf(
            "ผู้ให้บริการ: Nimit - แพลตฟอร์มสั่งอาหาร",
            "อีเมล: aittinon417@gmail.com",
            "LINE ผู้ดูแลระบบ: https://lin.ee/6rWI6rH"
        ))
    )

    val TERMS_EN: List<LegalSection> = listOf(
        LegalSection("1. Scope of service", listOf(
            "The Nimit Delivery app is a food-ordering platform for shops registered with us. You can order food, track orders, earn points, use coupons, and use the in-app social features and game.",
            "For adult users. If you are under 20, you should have a guardian's consent."
        )),
        LegalSection("2. Account and sign-in", listOf(
            "Sign in with your phone number and confirm with a one-time password (OTP) when enabled.",
            "You must provide accurate information (name, phone, delivery address) and keep your phone number and device secure."
        )),
        LegalSection("3. Ordering and payment", listOf(
            "An order is placed when you confirm it. Shops and riders prepare and deliver the food.",
            "Prices, delivery fees and payment methods are as shown on the checkout page before you confirm.",
            "For cancellations, refunds and order problems, contact the administrator on LINE or by email below."
        )),
        LegalSection("4. Points and coupons", listOf(
            "Loyalty points and coupons from food orders are not money, cannot be exchanged for cash, and follow each shop's or promotion's conditions.",
            "We may refuse or revoke coupons obtained by fraud, such as creating a new account to claim a benefit again."
        )),
        LegalSection("5. Social features", listOf(
            "Using social features requires creating an account and accepting this policy first.",
            "Do not post content that is illegal, obscene, abusive, harassing, hateful, deceptive, or that infringes others' rights.",
            "You can report inappropriate content or users, block users, and delete your own content. We may remove content or suspend accounts that violate these terms."
        )),
        LegalSection("6. In-app game", listOf(
            "The game is free and for entertainment while you wait for orders. There is no betting or real-money purchase.",
            "Game points and items can only be used in the game, cannot be exchanged for money or goods, and are separate from food-order loyalty points."
        )),
        LegalSection("7. Suspension and account deletion", listOf(
            "We may suspend accounts that violate these terms or show fraudulent behavior.",
            "You can request deletion of your account and data via the email or LINE below (see the Privacy Policy for details)."
        )),
        LegalSection("8. Limitation of liability", listOf(
            "The service is provided as is. To the extent permitted by law, we are not liable for damage caused by network failures, force majeure, or use that violates these terms. Shops are responsible for the quality of their food."
        )),
        LegalSection("9. Governing law and changes", listOf(
            "These terms are governed by the laws of the Kingdom of Thailand.",
            "We may update these terms from time to time and show the version number on this page. Continued use means you accept the updated version."
        )),
        LegalSection("10. Operator and contact", listOf(
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
            "ข้อมูลบัญชี: ชื่อ เบอร์โทร และที่อยู่จัดส่ง พร้อมพิกัดที่คุณปักหมุด",
            "ข้อมูลออเดอร์: รายการอาหาร ยอดเงิน สถานะ ประวัติสั่งซื้อ แต้มสะสม และคูปอง",
            "ข้อมูลโซเชียลและเกม: ชื่อที่แสดง เนื้อหาที่คุณโพสต์ รายงานและการบล็อก และความคืบหน้าในเกม",
            "ข้อความแชทกับร้านหรือไรเดอร์ รวมถึงรูปภาพที่คุณส่ง",
            "ข้อมูลเทคนิค: รหัสอุปกรณ์สำหรับส่งการแจ้งเตือน ประเภทอุปกรณ์ และข้อมูลการเข้าใช้งานระบบ (log)",
            "ตำแหน่ง: ใช้เฉพาะเมื่อคุณกดใช้ตำแหน่งปัจจุบันเพื่อปักหมุดที่อยู่ เราไม่ติดตามตำแหน่งของคุณเบื้องหลัง"
        )),
        LegalSection("3. เราใช้ข้อมูลทำอะไร และฐานทางกฎหมาย", listOf(
            "ยืนยันตัวตนและให้คุณเข้าสู่ระบบ รับและจัดส่งออเดอร์ (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "ส่งการแจ้งเตือนสถานะออเดอร์ (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "ให้บริการแต้มสะสม คูปอง โซเชียล และเกม (เพื่อปฏิบัติตามสัญญาให้บริการ)",
            "ป้องกันการทุจริต เช่น การรับคูปองซ้ำ และดูแลเนื้อหาที่ไม่เหมาะสม (เพื่อประโยชน์โดยชอบด้วยกฎหมาย)",
            "ใช้ตำแหน่งปัจจุบันเพื่อปักหมุดที่อยู่ (โดยความยินยอมของคุณ ซึ่งถอนได้ที่การตั้งค่าเครื่อง)",
            "เก็บข้อมูลธุรกรรมตามที่กฎหมายกำหนด (เพื่อปฏิบัติตามกฎหมาย)"
        )),
        LegalSection("4. เราเปิดเผยข้อมูลให้ใครบ้าง", listOf(
            "ร้านที่คุณสั่ง: ชื่อ เบอร์โทร ที่อยู่ และรายการสั่งซื้อ",
            "ไรเดอร์ที่รับงาน: ชื่อ เบอร์โทร ที่อยู่จัดส่ง และรายการสั่งซื้อ เพื่อนำส่งอาหาร",
            "ผู้ใช้อื่น: ชื่อที่แสดงและเนื้อหาที่คุณโพสต์ในโซเชียล",
            "ผู้ดูแลระบบ Nimit Delivery",
            "ผู้ให้บริการที่ช่วยให้ระบบทำงาน ได้แก่ Google Firebase (ยืนยันตัวตนด้วย OTP ฐานข้อมูล และการแจ้งเตือน), Cloudinary (เก็บรูปภาพ), Vercel (ส่งการแจ้งเตือน) และผู้ให้บริการแผนที่ (Esri / OpenStreetMap)",
            "หน่วยงานรัฐเมื่อมีกฎหมายหรือคำสั่งที่มีอำนาจกำหนด",
            "เราไม่ขายข้อมูลของคุณ"
        )),
        LegalSection("5. ที่เก็บข้อมูลและการส่งไปต่างประเทศ", listOf(
            "ระบบและฐานข้อมูลทำงานบนคลาวด์ของผู้ให้บริการ ได้แก่ Google Cloud / Firebase (Cloud Firestore เป็นฐานข้อมูลหลัก), Cloudinary (จัดเก็บรูปภาพ) และ Vercel (โฮสต์เว็บไซต์และบริการส่งการแจ้งเตือน)",
            "ข้อมูลอาจถูกจัดเก็บและสำรองในศูนย์ข้อมูลของผู้ให้บริการที่ตั้งอยู่ต่างประเทศ",
            "เราเลือกผู้ให้บริการที่มีมาตรฐานการคุ้มครองข้อมูลในระดับที่เหมาะสม ข้อมูลถูกเข้ารหัสระหว่างส่งและขณะจัดเก็บ และผู้ให้บริการอยู่ภายใต้ข้อตกลงการประมวลผลข้อมูลที่ผูกพันให้คุ้มครองข้อมูลของคุณ"
        )),
        LegalSection("6. ระยะเวลาเก็บข้อมูล", listOf(
            "ข้อมูลบัญชี ที่อยู่ โซเชียล และเกม: เก็บตราบที่บัญชียังใช้งาน และลบภายใน 30 วัน หลังยืนยันคำขอลบบัญชีสำเร็จ",
            "ข้อมูลออเดอร์และธุรกรรม: เก็บไว้ 5 ปี ตามที่กฎหมายการบัญชีกำหนด แล้วลบหรือทำให้ไม่สามารถระบุตัวตนได้",
            "บันทึกป้องกันการรับคูปองซ้ำ: เก็บเฉพาะค่าที่แปลงจากเบอร์โทรแบบไม่สามารถย้อนกลับได้ (hash) เท่าที่จำเป็นต่อการป้องกันการทุจริต แม้หลังลบบัญชี",
            "ข้อมูลการเข้าใช้งานระบบ (log): เก็บตามที่กฎหมายว่าด้วยการกระทำความผิดเกี่ยวกับคอมพิวเตอร์กำหนด (ไม่น้อยกว่า 90 วัน)",
            "โทเคนแจ้งเตือน: ลบเมื่อออกจากระบบหรือลบบัญชี"
        )),
        LegalSection("7. สิทธิ์ของคุณตาม พ.ร.บ.คุ้มครองข้อมูลส่วนบุคคล พ.ศ. 2562", listOf(
            "ขอเข้าถึงและขอรับสำเนาข้อมูลของคุณ รวมถึงขอให้โอนย้ายข้อมูล",
            "ขอแก้ไขข้อมูลให้ถูกต้อง ขอให้ลบ ทำลาย หรือทำให้ไม่สามารถระบุตัวตนได้",
            "คัดค้านหรือขอให้ระงับการใช้ข้อมูล และถอนความยินยอมได้ทุกเมื่อ (เช่น ปิดสิทธิ์ตำแหน่งที่การตั้งค่าเครื่อง)",
            "ช่องทางใช้สิทธิ และขอลบบัญชี: ส่งคำขอทางอีเมล aittinon417@gmail.com หรือทาง LINE ผู้ดูแลระบบ เราจะดำเนินการภายใน 30 วันนับจากยืนยันตัวตนสำเร็จ ยกเว้นข้อมูลที่กฎหมายกำหนดให้เก็บ",
            "หากเห็นว่าเราไม่ปฏิบัติตามกฎหมาย คุณมีสิทธิร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล (สคส.)"
        )),
        LegalSection("8. ความปลอดภัย", listOf(
            "ข้อมูลส่งผ่านการเชื่อมต่อที่เข้ารหัส และจำกัดการเข้าถึงเฉพาะผู้ที่เกี่ยวข้อง แต่ไม่มีระบบใดปลอดภัย 100% กรุณารักษาเบอร์โทรและอุปกรณ์ของคุณให้ปลอดภัย"
        )),
        LegalSection("9. ข้อมูลของเด็ก", listOf(
            "แอปนี้สำหรับผู้บรรลุนิติภาวะ (อายุตั้งแต่ 20 ปี) หากอายุต่ำกว่านั้น ต้องได้รับความยินยอมจากผู้ปกครอง เราไม่ตั้งใจเก็บข้อมูลของผู้เยาว์โดยไม่มีความยินยอม",
            "หากพบว่ามีข้อมูลของผู้เยาว์โดยไม่ชอบ เราจะลบข้อมูลนั้นโดยเร็ว"
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
            "Account data: name, phone number, delivery address and the location you pin.",
            "Order data: items, amounts, status, order history, loyalty points and coupons.",
            "Social and game data: display name, content you post, reports and blocks, and game progress.",
            "Chat messages with shops or riders, including photos you send.",
            "Technical data: device token for notifications, device type, and system access logs.",
            "Location: used only when you tap to use your current location to pin an address. We do not track your location in the background."
        )),
        LegalSection("3. How we use data and legal bases", listOf(
            "Verify your identity, let you sign in, and process and deliver orders (to perform the service contract).",
            "Send order-status notifications (to perform the service contract).",
            "Provide points, coupons, social features and the game (to perform the service contract).",
            "Prevent fraud such as repeated coupon claims, and moderate inappropriate content (legitimate interests).",
            "Use your current location to pin an address (with your consent, which you can withdraw in device settings).",
            "Keep transaction records as required by law (legal obligation)."
        )),
        LegalSection("4. Who we share data with", listOf(
            "The shop you order from: your name, phone number, address and order items.",
            "The rider who takes the job: your name, phone number, delivery address and order items, to deliver the food.",
            "Other users: your display name and content you post in social features.",
            "The Nimit Delivery administrator.",
            "Service providers that make the system work: Google Firebase (OTP sign-in, database, notifications), Cloudinary (image storage), Vercel (sending notifications) and map providers (Esri / OpenStreetMap).",
            "Government authorities when required by law or a lawful order.",
            "We do not sell your data."
        )),
        LegalSection("5. Where data is stored and international transfers", listOf(
            "The system and database run on cloud providers: Google Cloud / Firebase (Cloud Firestore is the main database), Cloudinary (image storage) and Vercel (website hosting and the notification service).",
            "Data may be stored and backed up in the providers' data centers located outside Thailand.",
            "We choose providers with appropriate data-protection standards; data is encrypted in transit and at rest, and the providers are bound by data processing terms to protect your data."
        )),
        LegalSection("6. Retention", listOf(
            "Account, address, social and game data: kept while the account is active and deleted within 30 days after an account deletion request is verified.",
            "Order and transaction data: kept for 5 years as required by accounting law, then deleted or anonymized.",
            "Anti-abuse coupon record: only an irreversible hash of the phone number is kept, as long as needed to prevent fraud, even after account deletion.",
            "System access logs: kept as required by the Computer Crime Act (at least 90 days).",
            "Notification tokens: deleted on sign-out or account deletion."
        )),
        LegalSection("7. Your rights under Thailand's Personal Data Protection Act B.E. 2562 (2019)", listOf(
            "Access and obtain a copy of your data, and request data portability.",
            "Correct inaccurate data, and request deletion, destruction or anonymization.",
            "Object to or restrict the use of your data, and withdraw consent at any time (for example, turn off location permission in device settings).",
            "How to exercise your rights and request account deletion: email aittinon417@gmail.com or message the administrator on LINE. We will act within 30 days after your identity is verified, except for data we are required by law to keep.",
            "If you believe we do not comply with the law, you may complain to the Personal Data Protection Committee Office (PDPC)."
        )),
        LegalSection("8. Security", listOf(
            "Data is sent over encrypted connections and access is limited to relevant people, but no system is 100% secure. Please keep your phone number and device safe."
        )),
        LegalSection("9. Children's data", listOf(
            "This app is for adults (20 or older). Younger users need a guardian's consent. We do not knowingly collect minors' data without consent.",
            "If we find any minor's data collected improperly, we will delete it promptly."
        )),
        LegalSection("10. Changes to this policy", listOf(
            "We may update this policy from time to time and show the version number on this page."
        ))
    )
}
