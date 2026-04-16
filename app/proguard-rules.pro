# 1. إيقاف التشفير (لتبقى الأسماء واضحة في الكراش)
-dontobfuscate

# 2. الحفاظ على أرقام الأسطر 
-keepattributes SourceFile, LineNumberTable

# 3. حماية الكود الخاص بك وكلاسات BuildConfig


# ---------------------------------------------------------
# 4. حماية مكتبة OneUI Design (إبقاء الحماية كما طلبت)
# ---------------------------------------------------------

# ---------------------------------------------------------
# 5. إلغاء الحماية عن SESL و Lottie (للتخلص من الملفات الزائدة)
# ---------------------------------------------------------
# ملاحظة: حذفنا أسطر الـ -keep لـ io.github.oneuiproject.sesl و com.airbnb.lottie
# لترك المحرك يحذف كل ما لا يراه مستخدماً بشكل صريح.

# 6. تجاهل التحذيرات (ضروري جداً لتجنب فشل البناء بسبب الحذف العنيف)
-dontwarn io.github.oneuiproject.sesl.**
-dontwarn com.airbnb.lottie.**
-dontwarn javax.annotation.**
-dontwarn okio.**
# هذا الأمر يخبر المحرك بطباعة قائمة الكود غير المستخدم في ملف اسمه usage.txt
-printusage usage.txt
-keep class dev.oneuiproject.oneui.** {*;}
-keep class com.example.oneuiapp.** {*;}



