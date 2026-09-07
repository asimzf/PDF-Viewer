# PDFBox-Android ships resources and reflective font/parser lookups.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**

# PDFBox reaches Bouncy Castle through the JCE provider, which is looked up by
# name, so the shrinker cannot see those classes are needed.
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-dontwarn org.bouncycastle.**

-dontwarn org.apache.commons.logging.**
-dontwarn javax.naming.**
