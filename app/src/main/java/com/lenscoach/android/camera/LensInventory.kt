package com.lenscoach.android.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.SizeF
import com.lenscoach.android.R
import java.util.Locale
import kotlin.math.abs

enum class LensRole {
    ULTRA_WIDE,
    WIDE,
    TELE,
    FRONT,
    OTHER,
}

data class CameraUnit(
    val id: String,
    val facing: Int,
    val focalMm: Float,
    val equiv35: Float,
    val aperture: Float?,
    val minZoom: Float,
    val maxZoom: Float,
    val logical: Boolean,
    val physicalIds: List<String>,
    val role: LensRole,
)

data class LensStep(
    val id: String,
    val label: String,
    val zoom: Float,
    val role: LensRole,
    val optical: Boolean,
)

data class LensPart(
    val role: LensRole,
    val spec: String,
)

data class CameraInventory(
    val units: List<CameraUnit>,
    val backSteps: List<LensStep>,
    val backCount: Int,
    val parts: List<LensPart>,
)

object LensInventory {
    fun probe(context: Context): CameraInventory {
        val manager = context.getSystemService(CameraManager::class.java)
        val seen = linkedSetOf<String>()
        val units = mutableListOf<CameraUnit>()
        for (id in manager.cameraIdList) {
            collect(manager, id, seen, units)
        }
        val back = units.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        val logical = back.firstOrNull { it.logical } ?: back.maxByOrNull { it.maxZoom - it.minZoom }
        val steps = buildSteps(logical, back)
        val parts = buildParts(back, steps)
        val count = physicalBackCount(back)
        return CameraInventory(
            units = units,
            backSteps = steps,
            backCount = count,
            parts = parts,
        )
    }

    fun stepsForZoomRange(minZoom: Float, maxZoom: Float, fallback: List<LensStep>): List<LensStep> {
        if (minZoom <= 0f || maxZoom < minZoom) return fallback
        val logical = CameraUnit(
            id = "live",
            facing = CameraCharacteristics.LENS_FACING_BACK,
            focalMm = 0f,
            equiv35 = 0f,
            aperture = null,
            minZoom = minZoom,
            maxZoom = maxZoom,
            logical = true,
            physicalIds = emptyList(),
            role = LensRole.WIDE,
        )
        val built = buildSteps(logical, emptyList())
        return built.ifEmpty { fallback }
    }

    private fun collect(
        manager: CameraManager,
        id: String,
        seen: MutableSet<String>,
        out: MutableList<CameraUnit>,
    ) {
        if (!seen.add(id)) return
        val chars = runCatching { manager.getCameraCharacteristics(id) }.getOrNull() ?: return
        val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: return
        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val focal = focals?.firstOrNull() ?: 0f
        val sensor: SizeF? = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val equiv = if (focal > 0f && sensor != null && sensor.width > 0f) {
            focal * 36f / sensor.width
        } else {
            0f
        }
        val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        val minZoom = zoomRange?.lower ?: 1f
        val maxZoom = zoomRange?.upper
            ?: chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1f
        val physicalIds = if (Build.VERSION.SDK_INT >= 28) {
            chars.physicalCameraIds.toList()
        } else {
            emptyList()
        }
        val logical = physicalIds.size >= 2
        out += CameraUnit(
            id = id,
            facing = facing,
            focalMm = focal,
            equiv35 = equiv,
            aperture = apertures?.firstOrNull(),
            minZoom = minZoom,
            maxZoom = maxZoom,
            logical = logical,
            physicalIds = physicalIds,
            role = roleFor(facing, equiv, minZoom),
        )
        physicalIds.forEach { pid -> collect(manager, pid, seen, out) }
    }

    private fun roleFor(facing: Int, equiv35: Float, minZoom: Float): LensRole {
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return LensRole.FRONT
        if (equiv35 > 0f) {
            return when {
                equiv35 < 20f -> LensRole.ULTRA_WIDE
                equiv35 < 40f -> LensRole.WIDE
                else -> LensRole.TELE
            }
        }
        return when {
            minZoom < 0.85f -> LensRole.ULTRA_WIDE
            else -> LensRole.WIDE
        }
    }

    private fun buildSteps(logical: CameraUnit?, back: List<CameraUnit>): List<LensStep> {
        val min = logical?.minZoom ?: back.minOfOrNull { it.minZoom } ?: 1f
        val max = logical?.maxZoom ?: back.maxOfOrNull { it.maxZoom } ?: 1f
        val steps = mutableListOf<LensStep>()
        fun add(id: String, zoom: Float, role: LensRole, optical: Boolean, label: String? = null) {
            if (zoom < min - 0.02f || zoom > max + 0.02f) return
            val clamped = zoom.coerceIn(min, max)
            if (steps.any { abs(it.zoom - clamped) < 0.08f }) return
            val text = label ?: formatZoom(clamped)
            steps += LensStep(id, text, clamped, role, optical)
        }
        if (min < 0.9f) add("uw", min, LensRole.ULTRA_WIDE, optical = true)
        add("wide", 1f, LensRole.WIDE, optical = true)
        when {
            max >= 3.0f -> add("tele", if (max >= 3.2f) 3.2f else 3f, LensRole.TELE, optical = true)
            max >= 1.85f -> add("tele", 2f, LensRole.TELE, optical = true)
        }
        if (max >= 4.6f) add("super", 5f, LensRole.TELE, optical = false, label = "5x")
        if (steps.isEmpty()) add("wide", 1f.coerceIn(min, max), LensRole.WIDE, optical = true)
        return steps.sortedBy { it.zoom }
    }

    private fun formatZoom(zoom: Float): String {
        val rounded = if (abs(zoom - zoom.toInt()) < 0.05f) {
            "${zoom.toInt()}x"
        } else {
            "${"%.1f".format(Locale.US, zoom)}x"
        }
        return rounded
    }

    private fun physicalBackCount(back: List<CameraUnit>): Int {
        val physical = back.filter { !it.logical }
        return physical.ifEmpty { back }.distinctBy { it.id }.size
    }

    private fun buildParts(back: List<CameraUnit>, steps: List<LensStep>): List<LensPart> {
        val physical = back.filter { !it.logical }.distinctBy { it.role }
            .sortedBy { it.equiv35.takeIf { v -> v > 0f } ?: it.minZoom }
        if (physical.isNotEmpty()) {
            return physical.map { unit ->
                val spec = if (unit.equiv35 > 0f) "${unit.equiv35.toInt()}mm" else unit.id
                LensPart(unit.role, spec)
            }
        }
        return steps.map { step ->
            LensPart(step.role, step.label)
        }
    }
}

fun List<LensStep>.nearest(zoom: Float): LensStep? =
    minByOrNull { abs(it.zoom - zoom) }

fun LensRole.captionRes(): Int = when (this) {
    LensRole.ULTRA_WIDE -> R.string.lens_ultra_wide
    LensRole.TELE -> R.string.lens_tele
    else -> R.string.lens_wide
}
