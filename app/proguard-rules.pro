# PDFBox-Android ships resources and reflective font/parser lookups.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.naming.**
