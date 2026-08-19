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
 * Ads Regex Advance — fixed:
 * - Skip methods with null implementation
 * - No replaceInstruction(..., "nop") (causes Collection is empty)
 * - Only early-return on SDK methods + force boolean results
 */
@Suppress("unused")
val adsRegexAdvancePatch = bytecodePatch(
    name = "Ads Regex Advance",
    description = "Advanced universal ad SDK killer (load/show/init across major networks).",
    default = false,
) {
    execute {
        val sdkPackages = listOf(
            "adcolony", "admob", "adsdk", "aerserv", "appbrain",
            "applovin", "appodeal", "appodealx", "appsflyer",
            "bytedance/sdk/openadsdk", "chartboost", "flurry", "fyber",
            "hyprmx", "inmobi", "ironsource", "mbrg", "mbridge",
            "mintegral", "moat", "mobfox", "mobilefuse", "mopub",
            "my/target", "ogury", "omid", "onesignal", "presage",
            "smaato", "smartadserver", "snap/adkit", "snap/appadskit",
            "startapp", "taboola", "tapjoy", "tappx", "vungle",
            "unity3d/ads", "gms/ads", "facebook/ads", "pangle",
        )

        val skipNameParts = listOf(
            "close", "destroy", "dismiss", "disabl", "error", "player",
            "remov", "expir", "fail", "hide", "skip", "stop", "throw", "deactiv",
        )

        val actionHints = listOf(
            "loadad", "load_ad", "showad", "show_ad",
            "loadinterstitial", "showinterstitial",
            "loadrewarded", "showrewarded",
            "loadbanner", "showbanner",
            "loadnative", "shownative",
            "loadappopen", "showappopen",
            "requestad", "fetchad", "displayad",
            "initad", "renderad", "refreshad",
        )

        val boolHints = listOf(
            "isloaded", "isready", "isadavailable", "canshow", "isshowing",
            "hasad", "isadloaded", "isadready",
        )

        fun String.isSdkClass(): Boolean =
            sdkPackages.any { contains(it, ignoreCase = true) }

        fun String.shouldSkip(): Boolean =
            skipNameParts.any { contains(it, ignoreCase = true) }

        fun String.hasAction(): Boolean =
            actionHints.any { contains(it, ignoreCase = true) }

        fun String.hasBoolAction(): Boolean =
            boolHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isSdk =
                className.isSdkClass() ||
                    className.contains("/ads/", true) ||
                    className.contains("AdView", true) ||
                    className.contains("AdLoader", true) ||
                    className.contains("AdRequest", true) ||
                    className.contains("AdActivity", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                // FIX 1: never touch null implementation
                if (method.instructionsOrNull == null) return@forEach

                val methodName = method.name
                val instructions = method.instructionsOrNull!!.toList()

                // ---- A) Inside ad SDK classes: early return ----
                if (isSdk && !methodName.shouldSkip()) {
                    when {
                        method.returnType == "V" && methodName.hasAction() -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                        method.returnType == "Z" &&
                            (methodName.hasBoolAction() || methodName.hasAction()) -> {
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

                // ---- B) Call-sites: only boolean force (NO nop) ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType

                    if (name.shouldSkip()) return@forEachIndexed
                    if (!def.isSdkClass() && !def.contains("/ads/", true)) return@forEachIndexed

                    val isAdCall = name.hasAction() || name.hasBoolAction()
                    if (!isAdCall) return@forEachIndexed

                    // Only boolean results — safe replaceInstruction
                    if (ret == "Z") {
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            method.replaceInstruction(
                                index + 1,
                                "const/4 v${next.registerA}, 0x0",
                            )
                        }
                    }
                    // Void invokes: do NOT use replaceInstruction(..., "nop")
                }
            }
        }
    }
}
