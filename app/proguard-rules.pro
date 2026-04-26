# ── Debugging ─────────────────────────────────────────────────────────────────
# Keep source file names and line numbers so crash stack traces are readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson / Retrofit models ────────────────────────────────────────────────────
# Gson deserializes these by field name via reflection; R8 must not rename or strip them
-keep class com.ycs.movietracker.data.model.TmdbSearchResult { *; }
-keep class com.ycs.movietracker.data.repository.TmdbSearchResponse { *; }
-keep class com.ycs.movietracker.data.repository.TmdbMovieDto { *; }
-keep class com.ycs.movietracker.data.repository.TmdbVideosResponse { *; }
-keep class com.ycs.movietracker.data.repository.TmdbVideoDto { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Firebase ──────────────────────────────────────────────────────────────────
# Prevent R8 from stripping Firebase component registrars (causes "component not present" crash)
-keep class com.google.firebase.components.ComponentRegistrar
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
# Preserve Kotlin metadata used by reflection (coroutines, serialization)
-keepattributes *Annotation*, Signature, Exception
-keep class kotlin.Metadata { *; }
