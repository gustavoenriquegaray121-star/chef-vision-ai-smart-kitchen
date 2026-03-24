package com.gustavo.chefvisionia

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var scanCount = 0
    private var userPlan = "GRATUITO"

    private lateinit var chipContainer: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var btnCart: ImageButton

    private val ingredientesDetectados = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chipContainer = findViewById(R.id.chipContainer)
        btnScan = findViewById(R.id.btnScanIngredients)
        btnCart = findViewById(R.id.btnGoToCart)

        // 🔥 CARGAR MEMORIA
        val guardados = MemoryManager.obtener(this)
        if (guardados.isNotEmpty()) {
            ingredientesDetectados.addAll(guardados)
            mostrarOpciones()
        }

        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                abrirCamara()
            } else {
                mostrarUpgrade()
            }
        }

        btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        // 🔥 SEMÁFORO (demo)
        evaluarFrescuraDemo()
    }

    private fun puedeEscanear(): Boolean {
        val limite = when(userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }

        return if (scanCount < limite) {
            scanCount++
            true
        } else false
    }

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            procesarImagen()
        }
    }

    // 🔥 SIMULACIÓN (luego IA real)
    private fun procesarImagen() {
        ingredientesDetectados.clear()
        ingredientesDetectados.addAll(listOf("huevo", "tocino", "cebolla"))

        MemoryManager.guardar(this, ingredientesDetectados)

        mostrarOpciones()
    }

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()

        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        opciones.forEach { receta ->

            val chip = TextView(this)
            chip.text = receta
            chip.setTextColor(Color.WHITE)
            chip.textSize = 14f
            chip.setPadding(40, 20, 40, 20)

            val shape = GradientDrawable()
            shape.cornerRadius = 60f
            shape.setColor(Color.parseColor("#FF5722"))

            chip.background = shape

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(16, 16, 16, 16)
            chip.layoutParams = params

            chip.setOnClickListener {

                val faltantes = SmartCartManager.detectarFaltantes(
                    receta,
                    ingredientesDetectados
                )

                if (faltantes.isNotEmpty()) {

                    val mensaje = "Te falta: ${faltantes.joinToString(", ")}\n\n¿Agregar al carrito?"

                    AlertDialog.Builder(this)
                        .setTitle("🛒 Faltantes detectados")
                        .setMessage(mensaje)
                        .setPositiveButton("Agregar") { _, _ ->

                            CartMemory.agregarLista(this, faltantes)

                            Toast.makeText(this, "Agregado al carrito", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this, CartActivity::class.java))
                        }
                        .setNegativeButton("Continuar") { _, _ ->
                            abrirReceta(receta)
                        }
                        .setNeutralButton("Tip") { _, _ ->
                            mostrarTip()
                        }
                        .show()

                } else {
                    abrirReceta(receta)
                }
            }

            chipContainer.addView(chip)
        }
    }

    private fun abrirReceta(receta: String) {
        val intent = Intent(this, RecipeActivity::class.java)
        intent.putExtra("RECETA", receta)
        startActivity(intent)
    }

    private fun mostrarUpgrade() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Mejora tu plan")
            .setMessage("Has alcanzado el límite de escaneos.\n\nPásate a Premium para más recetas sin límites.")
            .setPositiveButton("Ver planes", null)
            .setNegativeButton("Después", null)
            .show()
    }

    private fun mostrarTip() {
        Toast.makeText(
            this,
            "💡 Tip: Martes y jueves hay ofertas en frutas y verduras 🛒",
            Toast.LENGTH_LONG
        ).show()
    }

    // 🔥 SEMÁFORO DEMO
    private fun evaluarFrescuraDemo() {

        val mensaje = StringBuilder()

        mensaje.append("🔴 Usa hoy: Espinacas\n")
        mensaje.append("🟡 Pronto: Tomate\n")
        mensaje.append("🟢 Fresco: Cebolla\n\n")
        mensaje.append("💡 Tip: Aprovecha ofertas locales")

        AlertDialog.Builder(this)
            .setTitle("Estado de tu despensa")
            .setMessage(mensaje.toString())
            .setPositiveButton("OK", null)
            .show()
    }
}
