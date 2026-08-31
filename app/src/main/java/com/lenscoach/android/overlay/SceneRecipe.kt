package com.lenscoach.android.overlay

import com.lenscoach.android.camera.LensRole
import com.lenscoach.android.camera.SceneKind

/**
 * Photographic recipes distilled from common craft:
 * portrait (eyes on upper third, short-tele compression),
 * landscape (foreground–mid–background, horizon on a third, ultra-wide),
 * street (28–35mm feel, subject on a third, layered planes).
 */
enum class SceneRecipe(
    val frameAspect: Float,
    val subjectBias: Float,
    val fillRatio: Float,
    val headroom: Float,
    val preferredLens: LensRole,
    val horizonY: Float,
) {
    PORTRAIT(
        frameAspect = 4f / 5f,
        subjectBias = 0.38f,
        fillRatio = 0.34f,
        headroom = 0.16f,
        preferredLens = LensRole.TELE,
        horizonY = 0.33f,
    ),
    LANDSCAPE(
        frameAspect = 16f / 9f,
        subjectBias = 0.50f,
        fillRatio = 0f,
        headroom = 0.08f,
        preferredLens = LensRole.ULTRA_WIDE,
        horizonY = 0.64f,
    ),
    STREET(
        frameAspect = 3f / 2f,
        subjectBias = 0.33f,
        fillRatio = 0.28f,
        headroom = 0.12f,
        preferredLens = LensRole.WIDE,
        horizonY = 0.45f,
    );

    companion object {
        fun from(scene: SceneKind): SceneRecipe = when (scene) {
            SceneKind.PORTRAIT, SceneKind.PET -> PORTRAIT
            SceneKind.LANDSCAPE, SceneKind.ARCHITECTURE -> LANDSCAPE
            SceneKind.FOOD, SceneKind.MACRO -> PORTRAIT
            SceneKind.STREET, SceneKind.GROUP, SceneKind.UNKNOWN -> STREET
        }
    }
}
