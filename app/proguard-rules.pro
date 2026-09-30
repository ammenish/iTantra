# ProGuard rules for iTantra
# Team MIRAGE â€” Offline Voice Communication

# Keep ONNX Runtime JNI
-keep class ai.onnxruntime.** { *; }

# Keep Sherpa-ONNX JNI (Phase 3+)
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Keep Room entities
-keep class com.mirage.itantra.data.local.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.** { *; }
