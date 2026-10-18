# kotlinx.serialization : les sérialiseurs sont générés et référencés par réflexion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.ygo.** { *** Companion; }
-keepclasseswithmembers class dev.ygo.** { kotlinx.serialization.KSerializer serializer(...); }
