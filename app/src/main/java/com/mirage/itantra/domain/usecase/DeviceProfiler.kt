package com.mirage.itantra.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device efficiency profiler for PS evaluation.
 *
 * Measures and reports the metrics required by the Efficiency criterion (20%):
 * - APK size
 * - Model file sizes
 * - Peak RAM usage
 * - Idle CPU usage
 * - Active CPU usage during STT inference
 * - Total storage footprint
 *
 * USAGE:
 *   val profiler = DeviceProfiler(context)
 *   val snapshot = profiler.captureSnapshot("idle")
 *   // ... trigger STT ...
 *   val activeSnapshot = profiler.captureSnapshot("active_stt")
 *   Log.i("Profile", profiler.generateReport(listOf(snapshot, activeSnapshot)))
 */
@Singleton
class DeviceProfiler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceProfiler"
    }

    /**
     * A snapshot of device resource usage at a specific point in time.
     */
    data class ResourceSnapshot(
        val label: String,
        val timestampMs: Long = System.currentTimeMillis(),

        // ── Memory ──
        /** Java heap used (bytes) */
        val javaHeapUsedBytes: Long = 0,
        /** Java heap max (bytes) */
        val javaHeapMaxBytes: Long = 0,
        /** Native heap used (bytes) — includes ONNX Runtime allocations */
        val nativeHeapUsedBytes: Long = 0,
        /** Total PSS (Proportional Set Size) — real memory footprint */
        val totalPssKb: Long = 0,

        // ── CPU ──
        /** Process CPU time in ms since process start */
        val processCpuTimeMs: Long = 0,

        // ── Storage ──
        /** APK file size (bytes) */
        val apkSizeBytes: Long = 0,
        /** Total data directory size (bytes) — includes models, caches */
        val dataDirSizeBytes: Long = 0,
        /** Individual model file sizes */
        val modelSizes: Map<String, Long> = emptyMap(),

        // ── Device Info ──
        val deviceModel: String = "",
        val androidVersion: String = "",
        val cpuAbi: String = "",
        val totalRamMb: Long = 0
    ) {
        fun toLogString(): String = buildString {
            appendLine("── Resource Snapshot: $label ──")
            appendLine("  Java Heap:     ${formatMb(javaHeapUsedBytes)} / ${formatMb(javaHeapMaxBytes)}")
            appendLine("  Native Heap:   ${formatMb(nativeHeapUsedBytes)}")
            appendLine("  Total PSS:     ${totalPssKb / 1024} MB")
            appendLine("  CPU Time:      ${processCpuTimeMs}ms")
            if (apkSizeBytes > 0) appendLine("  APK Size:      ${formatMb(apkSizeBytes)}")
            if (dataDirSizeBytes > 0) appendLine("  Data Dir:      ${formatMb(dataDirSizeBytes)}")
            modelSizes.forEach { (name, size) ->
                appendLine("  Model ($name): ${formatMb(size)}")
            }
            if (deviceModel.isNotBlank()) {
                appendLine("  Device:        $deviceModel")
                appendLine("  Android:       $androidVersion")
                appendLine("  ABI:           $cpuAbi")
                appendLine("  Total RAM:     ${totalRamMb} MB")
            }
        }

        private fun formatMb(bytes: Long): String {
            return "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }

    /**
     * Capture a snapshot of current resource usage.
     *
     * @param label Description of when this snapshot was taken (e.g., "idle", "active_stt")
     * @param includeStorage Whether to measure APK and data directory sizes (slower)
     * @param includeDeviceInfo Whether to include device hardware info
     */
    fun captureSnapshot(
        label: String,
        includeStorage: Boolean = false,
        includeDeviceInfo: Boolean = false
    ): ResourceSnapshot {
        val runtime = Runtime.getRuntime()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Memory info
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)

        // Process CPU time
        val cpuTimeMs = Process.getElapsedCpuTime()

        val snapshot = ResourceSnapshot(
            label = label,
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaHeapMaxBytes = runtime.maxMemory(),
            nativeHeapUsedBytes = Debug.getNativeHeapAllocatedSize(),
            totalPssKb = memInfo.totalPss.toLong(),
            processCpuTimeMs = cpuTimeMs,
            apkSizeBytes = if (includeStorage) getApkSize() else 0,
            dataDirSizeBytes = if (includeStorage) getDataDirSize() else 0,
            modelSizes = if (includeStorage) getModelSizes() else emptyMap(),
            deviceModel = if (includeDeviceInfo) "${Build.MANUFACTURER} ${Build.MODEL}" else "",
            androidVersion = if (includeDeviceInfo) "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})" else "",
            cpuAbi = if (includeDeviceInfo) Build.SUPPORTED_ABIS.joinToString(", ") else "",
            totalRamMb = if (includeDeviceInfo) getTotalRamMb(am) else 0
        )

        Log.d(TAG, snapshot.toLogString())
        return snapshot
    }

    /**
     * Generate a full efficiency report from multiple snapshots.
     * Designed for PS evaluation presentation.
     */
    fun generateReport(snapshots: List<ResourceSnapshot>): String = buildString {
        appendLine("╔══════════════════════════════════════════╗")
        appendLine("║     iTantra Efficiency Report             ║")
        appendLine("╚══════════════════════════════════════════╝")
        appendLine()

        snapshots.forEach { appendLine(it.toLogString()) }

        // Compute deltas if we have idle + active snapshots
        val idle = snapshots.find { it.label.contains("idle", ignoreCase = true) }
        val active = snapshots.find { it.label.contains("active", ignoreCase = true) }

        if (idle != null && active != null) {
            appendLine("── Active vs Idle Delta ──")
            appendLine("  ΔJava Heap:    +${formatMb(active.javaHeapUsedBytes - idle.javaHeapUsedBytes)}")
            appendLine("  ΔNative Heap:  +${formatMb(active.nativeHeapUsedBytes - idle.nativeHeapUsedBytes)}")
            appendLine("  ΔPSS:          +${(active.totalPssKb - idle.totalPssKb) / 1024} MB")
            appendLine("  ΔCPU Time:     +${active.processCpuTimeMs - idle.processCpuTimeMs}ms")
        }

        appendLine()
    }

    /**
     * Generate a markdown table for documentation.
     */
    fun generateMarkdownTable(snapshot: ResourceSnapshot): String = buildString {
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| APK Size | ${formatMb(snapshot.apkSizeBytes)} |")
        appendLine("| Java Heap | ${formatMb(snapshot.javaHeapUsedBytes)} / ${formatMb(snapshot.javaHeapMaxBytes)} |")
        appendLine("| Native Heap | ${formatMb(snapshot.nativeHeapUsedBytes)} |")
        appendLine("| Total PSS | ${snapshot.totalPssKb / 1024} MB |")
        snapshot.modelSizes.forEach { (name, size) ->
            appendLine("| Model ($name) | ${formatMb(size)} |")
        }
        if (snapshot.deviceModel.isNotBlank()) {
            appendLine("| Device | ${snapshot.deviceModel} |")
            appendLine("| Android | ${snapshot.androidVersion} |")
            appendLine("| ABI | ${snapshot.cpuAbi} |")
            appendLine("| Total RAM | ${snapshot.totalRamMb} MB |")
        }
    }

    // ── Private Helpers ──

    private fun getApkSize(): Long {
        return try {
            val appInfo = context.applicationInfo
            java.io.File(appInfo.sourceDir).length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get APK size", e)
            0L
        }
    }

    private fun getDataDirSize(): Long {
        return try {
            val dataDir = context.dataDir
            calculateDirSize(dataDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get data dir size", e)
            0L
        }
    }

    private fun getModelSizes(): Map<String, Long> {
        val sizes = mutableMapOf<String, Long>()
        try {
            // Check assets/models/ directory
            val modelDirs = context.assets.list("models") ?: emptyArray()
            for (dir in modelDirs) {
                val files = context.assets.list("models/$dir") ?: emptyArray()
                for (file in files) {
                    if (file.endsWith(".onnx") || file.endsWith(".bin")) {
                        try {
                            val fd = context.assets.openFd("models/$dir/$file")
                            sizes["$dir/$file"] = fd.length
                            fd.close()
                        } catch (e: Exception) {
                            // Compressed assets don't support openFd
                            Log.d(TAG, "Cannot measure $dir/$file (compressed asset)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate models", e)
        }
        return sizes
    }

    private fun calculateDirSize(dir: java.io.File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateDirSize(it) } ?: 0
    }

    private fun getTotalRamMb(am: ActivityManager): Long {
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    private fun formatMb(bytes: Long): String {
        return "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
