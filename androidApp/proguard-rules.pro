# R8 keep-rules for the release build (isMinifyEnabled = true).
#
# Most libraries here ship their own consumer rules (OkHttp, Room, Coil, Koin, ProfileInstaller),
# so we only add what those don't cover. The big one is kotlinx.serialization, whose generated
# serializers are looked up reflectively at runtime and must survive shrinking.

# ---- kotlinx.serialization (official rules) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the Companion of @Serializable types so `T.serializer()` resolves.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
# Keep `serializer()` on the companions of @Serializable types.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep INSTANCE + serializer() for @Serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the synthetic $serializer classes and their descriptors.
-keepclassmembers class **$$serializer {
    *** descriptor;
}

# Our DTOs/domain models are deserialized by generated serializers; keep them and their members
# so property names (which map to API JSON keys) survive shrinking.
-keep,includedescriptorclasses class com.deckpuller.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.deckpuller.** {
    <fields>;
}

# ---- Ktor ----
# Ktor reflectively wires some engines/plugins; silence warnings about optional integrations
# that aren't on the classpath, and keep its volatile state fields.
-dontwarn io.ktor.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# ---- Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
