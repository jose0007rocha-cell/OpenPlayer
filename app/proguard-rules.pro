# =====================================================================
# Regras do R8/ProGuard para o OpenPlayer (release)
# =====================================================================

# ---- Crashes legíveis: guarda arquivo e linha; use o mapping.txt do build para decifrar ----
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# ---- Media3 / ExoPlayer ----
# O Media3 já traz regras próprias (consumer rules). Abaixo, só o que o app usa por reflexão/JNI.

# Decodificador FFmpeg (Jellyfin): o DefaultRenderersFactory procura a classe pelo nome e o código
# nativo (libffmpegJNI.so) acessa classes do pacote por JNI.
-keep class androidx.media3.decoder.** { *; }
-keep class org.jellyfin.media3.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Glide ----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ---- DataBinding / ViewBinding ----
-keep class androidx.databinding.DataBinderMapperImpl { *; }
-keep class com.josercha.openplayer.databinding.** { *; }

# ---- Views próprias usadas nos layouts XML ----
-keep class com.josercha.openplayer.OutlinedTextView { <init>(...); }
-keep class com.josercha.openplayer.GradientTextView { <init>(...); }

# ---- Avisos de anotações de bibliotecas (não existem em tempo de execução) ----
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.jspecify.annotations.**
