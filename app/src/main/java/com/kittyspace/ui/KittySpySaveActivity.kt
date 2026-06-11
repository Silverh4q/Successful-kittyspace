package com.kittyspace.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class KittySpySaveActivity : Activity() {

    companion object {
        var dataToSave: String = ""
    }

    private val CREATE_FILE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileName = intent.getStringExtra("fileName") ?: "kittyspy.py"
        
        try {
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "KittyDumper/ManualSaves")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeText(dataToSave)
            Toast.makeText(this, com.kittyspace.ui.Obfuscator.o("JBYBEhNXBAIUFBIEBBECGxsOVwMYVzMYFAIaEhkDBFg8HgMDDjMCGgcSBVg6FhkCFhskFgESBFhTER4bEjkWGhI="), Toast.LENGTH_LONG).show()
        } catch(e: Exception) {
            Toast.makeText(this, com.kittyspace.ui.Obfuscator.o("JBYBElcRFh4bEhNNV1MMElkaEgQEFhASCg=="), Toast.LENGTH_LONG).show()
        }
        dataToSave = ""
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}


