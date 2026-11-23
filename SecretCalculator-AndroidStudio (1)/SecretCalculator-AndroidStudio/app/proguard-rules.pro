# Keep Room entities & Kotlin serialization models
-keep class com.secretcalculator.vault.data.local.entity.** { *; }
-keep class com.secretcalculator.vault.data.backup.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.secretcalculator.vault.data.backup.**$$serializer { *; }
