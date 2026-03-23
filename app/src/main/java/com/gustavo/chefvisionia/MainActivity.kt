package com.gustavo.chefvisionia

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var scanCount = 0
    private var userPlan = "GRATUITO"

    private lateinit var chipContainer: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var btnCart: ImageButton

    private val ingredientesDetectados = mutableListOf<String>()

    companion object {
        const val REQUEST_IMAGE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chipContainer = findViewById(R.id.chipContainer)
        btnScan = findViewById(R.id.btnScanIngredients)
        btnCart = findViewById(R.id.btnGoToCart)

        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                abrirCamara()
            } else {
                Toast.makeText(this, "Límite alcanzado para tu plan", Toast.LENGTH_LONG).show()
            }
        }

        btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    // 🔒 CONTROL DE PLANES
    private fun puedeEscanear(): Boolean {
        val limite = when (userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }

        return if (scanCount < limite) {
            scanCount++
            true
        } else {
            false
        }
    }

    // 📸 ABRIR CÁMARA
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, REQUEST_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al abrir cámara", Toast.LENGTH_SHORT).show()
        }
    }

    // 📸 RESULTADO DE CÁMARA
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE && resultCode == Activity.RESULT_OK) {
            procesarImagen()
        }
    }

    // 🧠 PROCESAMIENTO (AQUÍ VA IA DESPUÉS)
    private fun procesarImagen() {

        // 🔥 MOCK INTELIGENTE (temporal)
        ingredientesDetectados.clear()
        ingredientesDetectados.addAll(listOf("huevo", "tocino", "cebolla"))

        // 💾 GUARDAR MEMORIA LOCAL
        MemoryManager.guardar(this, ingredientesDetectados)

        // 🎯 GENERAR OPCIONES
        mostrarOpciones()
    }

    // 🍳 GENERAR BOTONES DINÁMICOS (CLAVE DE TU APP)
    private fun mostrarOpciones() {

        chipContainer.removeAllViews()

        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        if (opciones.isEmpty()) {
            Toast.makeText(this, "No se encontraron recetas", Toast.LENGTH_SHORT).show()
            return
        }

        opciones.forEach { receta ->

            val btn = Button(this)
            btn.text = receta

            btn.setOnClickListener {
                abrirReceta(receta)
            }

            chipContainer.addView(btn)
        }
    }

    // 🚀 SALTO AUTOMÁTICO A PANTALLA 2
    private fun abrirReceta(nombreReceta: String) {
        val intent = Intent(this, RecipeActivity::class.java)
        intent.putExtra("RECETA", nombreReceta)
        startActivity(intent)
    }
}
