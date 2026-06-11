package com.kittyspace.ui

import android.util.Base64

object Obfuscator {
    fun o(s: String): String {
        return String(Base64.decode(s, Base64.DEFAULT).map { (it.toInt() xor 0x77).toByte() }.toByteArray())
    }
}
