# جرد التطبيقات وتخفيف النظام

لا تستخدم أي أمر تعطيل/حذف قبل مراجعة تقرير الجهاز الحقيقي.

## جمع التقرير (Windows)
1. ثبّت Android Platform Tools إذا لم يكن أمر adb متاحاً.
2. فعّل USB debugging من خيارات المطوّر ووصل الجهاز بالكمبيوتر.
3. شغّل PowerShell من مجلد المشروع:
   powershell -ExecutionPolicy Bypass -File .\\tools\\windows\\collect-device-inventory.ps1
4. وافق على بصمة RSA على شاشة الجهاز إذا ظهرت.
5. أرسل ملف التقرير الناتج. الأداة للقراءة فقط.

## سياسة إزالة البرامج
- نبدأ بتعطيل حزم مؤكدة الاختيارية فقط، وليس حذف APK من /system.
- كل حزمة تُعطّل منفردة أو ضمن مجموعة صغيرة مع اختبار الإقلاع والوظائف.
- يجب حفظ أمر الرجوع: adb shell pm enable --user 0 PACKAGE_NAME
- لا تلمس حزم SystemUI أو Settings أو Launcher الحالي أو Package Installer أو الهاتف/الاتصال أو Bluetooth أو Wi-Fi أو Input/Touch أو Power/Battery أو Storage أو Media أو Location أو Google services الأساسية قبل تحليل اعتمادياتها.
- لا تستخدم debloat scripts عامة؛ قوائم الأجهزة الأخرى لا تنطبق تلقائياً على P01Y.
