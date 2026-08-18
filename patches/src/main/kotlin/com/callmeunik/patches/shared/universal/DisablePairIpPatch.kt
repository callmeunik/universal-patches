package com.callmeunik.patches.shared.universal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private val pairIpKeywords = listOf(
    "pairip",
    "Pairip",
    "PAIRIP",
    "com.pairip",
    "licensecheck",
    "LicenseActivity",
    "LicenseClient",
    "LicenseCheck",
    "VmChecker",
    "VMChecker",
    "BackgroundCheck",
    "pairipcore",
    "libpairip",
)

private fun String.looksLikePairIp(): Boolean =
    pairIpKeywords.any { contains(it, ignoreCase = true) }

/**
 * Step 1: Clean PairIP entries from AndroidManifest.xml
 */
private val disablePairIpResourcePatch = resourcePatch(
    description = "Removes PairIP-related components from the manifest.",
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val tags = listOf(
                "activity",
                "activity-alias",
                "receiver",
                "provider",
                "service",
                "meta-data",
            )

            tags.forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                val toRemove = mutableListOf<org.w3c.dom.Node>()

                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as? Element ?: continue
                    val nameAttr = element.getAttribute("android:name")
                    val valueAttr = element.getAttribute("android:value")
                    val resourceAttr = element.getAttribute("android:resource")

                    if (nameAttr.looksLikePairIp() ||
                        valueAttr.looksLikePairIp() ||
                        resourceAttr.looksLikePairIp()
                    ) {
                        toRemove.add(element)
                    }
                }

                toRemove.forEach { it.parentNode?.removeChild(it) }
            }

            // Remove intent-filters that point to PairIP actions
            val filters = doc.getElementsByTagName("intent-filter")
            val filtersToClean = mutableListOf<Element>()
            for (i in 0 until filters.length) {
                val filter = filters.item(i) as? Element ?: continue
                val children = filter.childNodes
                var hasPairIp = false
                for (j in 0 until children.length) {
                    val child = children.item(j) as? Element ?: continue
                    val actionName = child.getAttribute("android:name")
                    if (actionName.looksLikePairIp()) {
                        hasPairIp = true
                        break
                    }
                }
                if (hasPairIp) filtersToClean.add(filter)
            }
            filtersToClean.forEach { it.parentNode?.removeChild(it) }
        }
    }
}

/**
 * Step 2: Bytecode-level bypass for common PairIP license / VM checks
 */
@Suppress("unused")
val disablePairIpLicenseCheckPatch = bytecodePatch(
    name = "Disable PairIP license check",
    description = "Removes PairIP manifest components and bypasses common license, VM and background check calls.",
    default = false,
) {
    dependsOn(disablePairIpResourcePatch)

    execute {
        classDefForEach { classDef ->
            val className = classDef.type

            // If class itself is PairIP related, try to neutralize dangerous methods
            val isPairIpClass = className.looksLikePairIp()

            mutableClassDefBy(classDef).methods.forEach { method ->
                val methodName = method.name
                val instructions = method.instructionsOrNull?.toList() ?: return@forEach

                // 1) Neutralize methods inside PairIP classes
                if (isPairIpClass) {
                    when {
                        // boolean checks -> return false
                        method.returnType == "Z" &&
                            (methodName.contains("check", true) ||
                                methodName.contains("verify", true) ||
                                methodName.contains("isLicensed", true) ||
                                methodName.contains("isValid", true) ||
                                methodName.contains("shouldBlock", true) ||
                                methodName.contains("isEmulator", true) ||
                                methodName.contains("isDebugger", true) ||
                                methodName.contains("isRooted", true)) -> {
                            method.addInstructions(
                                0,
                                """
                                    const/4 v0, 0x0
                                    return v0
                                """.trimIndent(),
                            )
                            return@forEach
                        }

                        // void license / start / run checks -> return immediately
                        method.returnType == "V" &&
                            (methodName.contains("check", true) ||
                                methodName.contains("verify", true) ||
                                methodName.contains("start", true) ||
                                methodName.contains("run", true) ||
                                methodName.contains("execute", true) ||
                                methodName.contains("validate", true) ||
                                methodName.contains("show", true)) -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }

                        // int status codes -> return 0 (success / allowed)
                        method.returnType == "I" &&
                            (methodName.contains("check", true) ||
                                methodName.contains("verify", true) ||
                                methodName.contains("status", true) ||
                                methodName.contains("result", true)) -> {
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

                // 2) Patch call-sites that invoke PairIP methods from other classes
                instructions.forEachIndexed { index, instruction ->
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@forEachIndexed

                    val definingClass = reference.definingClass
                    val refName = reference.name

                    if (!definingClass.looksLikePairIp() && !refName.looksLikePairIp()) {
                        return@forEachIndexed
                    }

                    // After invoke, if next is move-result, force safe value
                    val next = instructions.getOrNull(index + 1)

                    when (reference.returnType) {
                        "Z" -> {
                            val move = next as? OneRegisterInstruction
                            if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${move.registerA}, 0x0",
                                )
                            }
                        }

                        "I" -> {
                            val move = next as? OneRegisterInstruction
                            if (move != null && move.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${move.registerA}, 0x0",
                                )
                            }
                        }

                        "V" -> {
                            // For void license calls, replace invoke with nop-like safe path:
                            // just skip by doing nothing extra (manifest removal covers most cases)
                        }
                    }

                    // Extra: if invoke-static/virtual to LicenseActivity style methods
                    val ins = instruction as? Instruction35c
                    if (ins != null &&
                        (refName.contains("startActivity", true) ||
                            refName.contains("showLicense", true) ||
                            refName.contains("block", true))
                    ) {
                        // Prefer not crashing: leave call, manifest already removed activity
                    }
                }
            }
        }
    }
}
