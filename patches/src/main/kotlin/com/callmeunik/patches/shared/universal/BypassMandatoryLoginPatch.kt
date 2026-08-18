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
 * Bypass Mandatory Login — skips forced login / isLoggedIn checks.
 */
@Suppress("unused")
val bypassMandatoryLoginPatch = bytecodePatch(
    name = "Bypass Mandatory Login",
    description = "Bypasses common mandatory login and isLoggedIn checks.",
    default = false,
) {
    execute {
        val loginHints = listOf(
            "isloggedin", "is_logged_in", "islogin", "is_login",
            "hasloggedin", "has_logged_in", "checklogin", "check_login",
            "requirelogin", "require_login", "needlogin", "need_login",
            "mustlogin", "must_login", "forcelogin", "force_login",
            "shouldlogin", "should_login", "loginrequired", "login_required",
            "isauthenticated", "is_authenticated", "isauth", "is_auth",
            "hassession", "has_session", "issignedin", "is_signed_in",
        )

        val skip = listOf("log", "track", "analytics", "error", "exception")

        fun String.hasLoginHint() = loginHints.any { contains(it, ignoreCase = true) }
        fun String.shouldSkip() = skip.any { contains(it, ignoreCase = true) }

        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                val name = method.name
                val instructions = method.instructionsOrNull?.toList()

                if (!name.shouldSkip() && name.hasLoginHint()) {
                    when (method.returnType) {
                        "Z" -> {
                            method.addInstructions(0, """
                                const/4 v0, 0x1
                                return v0
                            """.trimIndent())
                            return@forEach
                        }
                        "V" -> {
                            method.addInstructions(0, "return-void")
                            return@forEach
                        }
                    }
                }

                if (instructions == null) return@forEach

                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed

                    if (ref.name.shouldSkip()) return@forEachIndexed
                    if (!ref.name.hasLoginHint()) return@forEachIndexed

                    when (ref.returnType) {
                        "Z" -> {
                            val next = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                            if (next != null && next.opcode == Opcode.MOVE_RESULT) {
                                method.replaceInstruction(
                                    index + 1,
                                    "const/4 v${next.registerA}, 0x1"
                                )
                            }
                        }
                        "V" -> method.replaceInstruction(index, "nop")
                    }
                }
            }
        }
    }
}
