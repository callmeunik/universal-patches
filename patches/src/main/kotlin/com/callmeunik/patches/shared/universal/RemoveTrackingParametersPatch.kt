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
 * Remove Tracking Parameters from URLs
 * Neutralizes common tracking query parameters (utm_*, fbclid, gclid, etc.) where possible.
 */
@Suppress("unused")
val removeTrackingParametersPatch = bytecodePatch(
    name = "Remove Tracking Parameters from URLs",
    description = "Attempts to neutralize common tracking parameters (utm_*, fbclid, gclid, mc_eid, etc.) in URLs.",
    default = false,
) {
    execute {
        val trackingParams = listOf(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "utm_reader", "utm_name", "utm_social",
            "fbclid", "gclid", "dclid", "msclkid", "twclid",
            "mc_eid", "mc_cid", "igshid", "si", "ref",
            "referrer", "ref_src", "ref_url", "source",
            "campaign", "medium", "content", "term",
            "yclid", "zanpid", "gclsrc", "spm",
        )

        val urlHints = listOf(
            "buildupon", "appendquery", "appendqueryparameter",
            "addqueryparameter", "queryparameter", "setquery",
            "withquery", "putquery", "encode", "urlencode",
        )

        fun String.hasTracking() = trackingParams.any { contains(it, ignoreCase = true) }
        fun String.hasUrlHint() = urlHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // Look for tracking parameter strings and soften nearby URL builders
                instructions.forEachIndexed { index, instruction ->
                    val str = ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                        ?.string ?: return@forEachIndexed

                    if (!str.hasTracking()) return@forEachIndexed

                    // Nearby method calls that look like URL builders → try to nop or force empty
                    for (offset in 1..6) {
                        val nearby = instructions.getOrNull(index + offset) ?: continue
                        val ref = (nearby as? ReferenceInstruction)?.reference as? MethodReference
                            ?: continue

                        if (ref.name.hasUrlHint() || ref.definingClass.contains("Uri", true) ||
                            ref.definingClass.contains("URL", true) ||
                            ref.definingClass.contains("HttpUrl", true)
                        ) {
                            if (ref.returnType == "V") {
                                method.replaceInstruction(index + offset, "nop")
                            }
                        }
                    }
                }

                // Common share / tracking URL methods
                val name = method.name
                if (name.contains("share", true) || name.contains("track", true) ||
                    name.contains("utm", true) || name.contains("referrer", true)
                ) {
                    if (method.returnType == "Ljava/lang/String;") {
                        // Leave as-is for safety (rewriting string returns is risky without full context)
                    }
                }
            }
        }
    }
}
