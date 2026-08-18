package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * AdsRegexAdvance — advanced smali ad-regex pack as Morphe bytecode patch.
 * Targets major ad SDKs: AdMob, AppLovin, Unity, Vungle, IronSource, Pangle, etc.
 * Skips close/destroy/dismiss/hide/stop style methods.
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
            "remov", "expir", "fail", "hide", "skip", "stop", "throw",
            "deactiv",
        )

        val actionHints = listOf(
            "load", "show", "banner", "interstitial", "native", "rewarded",
            "initad", "init", "fetchad", "refresh", "render", "request",
            "videoad", "onad", "build", "report", "log", "track",
            "metadata", "header", "html", "javascript", "activate",
            "event", "exec", "response", "url", "property", "metric",
        )

        val boolHints = listOf(
            "load", "show", "banner", "interstitial", "native", "rewarded",
            "canad", "getad", "hasad", "isad", "wasad", "fetchad",
            "isloaded", "isready", "isshowing", "canshow",
        )

        val adUrlHints = listOf(
            "admob", "doubleclick", "googlesyndication", "googleads", "pagead",
            "adservice", "applovin", "unityads", "vungle", "adcolony",
            "chartboost", "inmobi", "mopub", "ironsource", "mintegral",
            "startappservice", "tapjoy", "flurry", "appsflyer", "moatads",
            "scorecardresearch", "googletagmanager", "crashlytics",
            "61.145.124.238", "2mdn.net", "-ads.", ".ads.", "/ads",
            "adsafeprotected", "pubmatic", "smaato", "taboola", "criteo",
            "appodeal", "appnext", "hyprmx", "presage.io", "rayjump",
        )

        fun String.isSdkClass(): Boolean =
            sdkPackages.any { contains(it, ignoreCase = true) }

        fun String.shouldSkipMethod(): Boolean =
            skipNameParts.any { contains(it, ignoreCase = true) }

        fun String.hasAction(): Boolean =
            actionHints.any { contains(it, ignoreCase = true) }

        fun String.hasBoolAction(): Boolean =
            boolHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isSdk = className.isSdkClass() ||
                className.contains("/ads/", true) ||
                className.contains("AdView", true) ||
                className.contains("AdLoader", true) ||
                className.contains("AdRequest", true) ||
                className.contains("AdActivity", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList()

                // ---- A) Inside SDK classes: neuter load/show methods ----
                if (isSdk && !methodName.shouldSkipMethod()) {
                    when {
                        method.returnType == "V" && methodName.hasAction() -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                        method.returnType == "Z" && methodName.hasBoolAction() -> {
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

                if (instructions == null) return@forEach

                // ---- B) Call-sites: nop void load/show on SDK classes ----
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val def = reference.definingClass
                    val name = reference.name
                    val ret = reference.returnType

                    if (name.shouldSkipMethod()) return@forEachIndexed
                    if (!def.isSdkClass() && !def.contains("/ads/", true)) return@forEachIndexed

                    val isLoadShow =
                        name.hasAction() ||
                            name.contains("request", true) ||
                            name.contains("Banner", true) ||
                            name.contains("Interstitial", true) ||
                            name.contains("Rewarded", true) ||
                            name.contains("Native", true)

                    if (!isLoadShow) return@forEachIndexed

                    when (ret) {
                        "V" -> method.replaceInstruction(index, "nop")
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0",
                                )
                            }
                        }
                    }
                }

                // ---- C) Ad network URL strings present → soften nearby boolean ----
                val hasAdUrl = instructions.any {
                    val s = ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
                        ?: return@any false
                    val lower = s.lowercase()
                    (lower.startsWith("http") || lower.startsWith("//")) &&
                        adUrlHints.any { h -> lower.contains(h) }
                }

                if (hasAdUrl) {
                    instructions.forEachIndexed { index, instruction ->
                        val reference =
                            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                                ?: return@forEachIndexed
                        if (reference.returnType != "Z") return@forEachIndexed
                        val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                        if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                            // only if method name looks ad-related
                            if (reference.name.hasBoolAction() ||
                                reference.definingClass.isSdkClass()
                            ) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x0",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
