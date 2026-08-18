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

/**
 * Disable SSL Pinning (Advanced + Certificate)
 * Strong SSL / certificate pinning bypass for OkHttp, TrustManager, Conscrypt, etc.
 * + forces network security config to trust user certificates.
 */
@Suppress("unused")
val disableSslPinningAdvancedPatch = bytecodePatch(
    name = "Disable SSL Pinning (Advanced)",
    description = "Advanced SSL and certificate pinning bypass (OkHttp, TrustManager, Conscrypt) + trust user certs.",
    default = false,
) {
    dependsOn(sslPinningNetworkSecurityPatch)

    execute {
        val pinHints = listOf(
            "checkservertrusted", "checkclienttrusted", "checktrusted",
            "certificatepinner", "certificate_pinner", "pinner",
            "sslpinning", "ssl_pinning", "pinning",
            "okhttp3.certificatepinner", "com.android.org.conscrypt",
            "trustmanager", "x509trustmanager", "hostnameverifier",
            "verifypin", "checkpin", "validatepin",
        )

        fun String.hasPinHint() = pinHints.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            val className = classDef.type
            val isPinClass = className.hasPinHint() ||
                className.contains("TrustManager", true) ||
                className.contains("CertificatePinner", true) ||
                className.contains("HostnameVerifier", true)

            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                // Neutralize pinning methods
                if (isPinClass || name.hasPinHint()) {
                    when (method.returnType) {
                        "V" -> {
                            if (name.contains("check", true) ||
                                name.contains("verify", true) ||
                                name.contains("pin", true)
                            ) {
                                method.addInstructions(0, "return-void")
                                return@forEach
                            }
                        }
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x1
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                // Call-sites
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (!ref.name.hasPinHint() && !ref.definingClass.hasPinHint()) return@forEachIndexed

                    when (ref.returnType) {
                        "V" -> method.replaceInstruction(index, "nop")
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x1"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val sslPinningNetworkSecurityPatch = resourcePatch(
    description = "Forces network security config to trust user + system certificates and cleartext.",
) {
    execute {
        // Create / overwrite network_security_config.xml
        val nscContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="true">
                    <trust-anchors>
                        <certificates src="system" />
                        <certificates src="user" />
                    </trust-anchors>
                </base-config>
                <debug-overrides>
                    <trust-anchors>
                        <certificates src="user" />
                        <certificates src="system" />
                    </trust-anchors>
                </debug-overrides>
            </network-security-config>
        """.trimIndent()

        // Write the file
        val nscFile = File("res/xml/network_security_config.xml")
        nscFile.parentFile?.mkdirs()
        nscFile.writeText(nscContent)

        // Point manifest to it
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@execute
            application.setAttribute("android:networkSecurityConfig", "@xml/network_security_config")
            application.setAttribute("android:usesCleartextTraffic", "true")
        }
    }
}
