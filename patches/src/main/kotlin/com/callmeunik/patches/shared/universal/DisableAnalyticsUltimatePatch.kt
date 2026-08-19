package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private val analyticsKeywords = listOf(
    "firebase", "crashlytics", "analytics",
    "adjust", "appsflyer", "mixpanel", "amplitude",
    "segment", "sentry", "bugsnag", "flurry",
    "localytics", "kissmetrics", "countly",
    "uxcam", "smartlook", "fullstory", "heap",
    "moengage", "clevertap", "webengage",
    "branch", "singular", "kochava", "tenjin",
    "com.google.android.gms.measurement",
    "com.google.firebase.analytics",
    "com.google.android.gms.analytics",
)

private fun String.isAnalyticsRelated() =
    analyticsKeywords.any { contains(it, ignoreCase = true) }

private val disableAnalyticsResourcePatch = resourcePatch(
    description = "Removes Firebase/analytics components from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            listOf("activity", "service", "receiver", "provider", "meta-data").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as? Element ?: continue
                    val name = el.getAttribute("android:name")
                    val value = el.getAttribute("android:value")
                    if (name.isAnalyticsRelated() || value.isAnalyticsRelated()) {
                        toRemove.add(el)
                    }
                }
                toRemove.forEach { it.parentNode?.removeChild(it) }
            }
        }
    }
}

@Suppress("unused")
val disableAnalyticsUltimatePatch = bytecodePatch(
    name = "Disable Analytics Ultimate",
    description = "Disables Firebase, Adjust, AppsFlyer, Mixpanel, Amplitude, Segment, Sentry and other common trackers.",
    default = false,
) {
    dependsOn(disableAnalyticsResourcePatch)

    execute {
        val actionHints = listOf(
            "logevent", "log_event", "track", "trackevent",
            "record", "send", "report", "log",
            "setuser", "set_user", "identify",
            "screen", "pageview", "revenue",
            "initialize", "init", "start", "enable",
            "capture", "enqueue", "flush", "upload",
        )

        val skipParts = listOf(
            "close", "destroy", "dispose", "release", "stop",
            "error", "exception", "throw", "fail",
        )

        fun String.hasAction() = actionHints.any { contains(it, ignoreCase = true) }
        fun String.shouldSkip() = skipParts.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val isSdk = classDef.type.isAnalyticsRelated()

            mutableClassDefBy(classDef).methods.forEach { method ->
                // Never touch methods without implementation
                if (method.instructionsOrNull == null) return@forEach

                val name = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // 1) Inside analytics SDK classes → early return
                if (isSdk && !name.shouldSkip() && name.hasAction()) {
                    when (method.returnType) {
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                        "Z" -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }
                    }
                }

                // 2) Call-sites: only force boolean results (NO nop replace)
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (ref.name.shouldSkip()) return@forEachIndexed

                    // Only analytics classes — avoid broad name matches
                    if (!ref.definingClass.isAnalyticsRelated()) return@forEachIndexed
                    if (!ref.name.hasAction()) return@forEachIndexed

                    if (ref.returnType == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                    // Void invokes: do NOT replaceInstruction(..., "nop")
                    // — that causes "Collection is empty" in Morphe smali compiler
                }
            }
        }
    }
}
