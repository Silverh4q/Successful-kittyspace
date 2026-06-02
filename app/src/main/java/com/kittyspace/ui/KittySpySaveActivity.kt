package com.kittyspace.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class KittySpySaveActivity : ComponentActivity() {

    companion object {
        var dataToSave: String = ""
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-python")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(dataToSave.toByteArray())
                }
                Toast.makeText(this, "Saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        dataToSave = ""
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileName = intent.getStringExtra("fileName") ?: "kittyspy.py"
        createDocument.launch(fileName)
    }
}
