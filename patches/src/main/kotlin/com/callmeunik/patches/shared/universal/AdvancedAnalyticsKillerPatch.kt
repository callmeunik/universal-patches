package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Advanced Analytics Killer — Firebase + Adjust + AppsFlyer + Mixpanel + Amplitude + Segment + more.
 */
@Suppress("unused")
val advancedAnalyticsKillerPatch = bytecodePatch(
    name = "Advanced Analytics Killer",
    description = "Disables Firebase, Adjust, AppsFlyer, Mixpanel, Amplitude, Segment and common trackers.",
    default = false,
) {
    execute {
        val sdkPackages = listOf(
            "firebase", "crashlytics", "analytics",
            "adjust", "appsflyer", "mixpanel", "amplitude",
            "segment", "sentry", "bugsnag", "flurry",
            "localytics", "kissmetrics", "countly",
            "uxcam", "smartlook", "fullstory", "heap",
            "moengage", "clevertap", "webengage",
            "branch", "singular", "kochava", "tenjin",
            "trafficjunky", "applovin", "ironsource",
        )

        val actionHints = listOf(
            "logevent", "log_event", "trackevent", "track_event",
            "track", "log", "record", "send", "report",
            "identify", "setuser", "set_user", "alias",
            "screen", "page", "purchase", "revenue",
            "init", "initialize", "start", "enable",
        )

        fun String.isAnalyticsSdk() = sdkPackages.any { contains(it, ignoreCase = true) }
        fun String.hasAction() = actionHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isSdk = className.isAnalyticsSdk()

            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (isSdk && name.hasAction()) {
                    when (method.returnType) {
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x0
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    val def = ref.definingClass
                    if (!def.isAnalyticsSdk() && !ref.name.hasAction()) return@forEachIndexed

                    when (ref.returnType) {
                        "V" -> method.replaceInstruction(index, "nop")
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
