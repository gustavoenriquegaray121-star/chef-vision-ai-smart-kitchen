package com.gustavo.chefvisionia

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// 🔥 IMPORTS ADMOB
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    // --- VARIABLES DE ESTADO ---
    private var scanCount = 0
    private var userPlan = "GRATUITO" // Podría moverse a SharedPreferences en el futuro

    // --- COMPONENTES UI ---
    private lateinit var chipContainer: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var btnCart: ImageButton
    private lateinit var txtPlan: TextView
    
    // 🔥 ADMOB
    private lateinit var adView: AdView

    // --- DATOS DE SESIÓN ---
    private val ingredientesDetectados = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nota: Asegúrate de que el XML se llame exactamente activity_main.xml
        setContentView(R.layout.activity_main)

        // 1. VINCULACIÓN DE VISTAS
        chipContainer = findViewById(R.id.chipContainer)
        btnScan = findViewById(R.id.btnScanIngredients)
        btnCart = findViewById(R.id.btnGoToCart)
        txtPlan = findViewById(R.id.txtPlan)

        // 2. 🔥 INICIALIZAR ADMOB (Configuración Segura)
        try {
            MobileAds.initialize(this) {}
            adView = findViewById(R.id.adView)
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } catch (e: Exception) {
            // Si AdMob falla, la app no debe cerrarse
            e.printStackTrace()
        }

        // 3. 🔥 CARGAR PERSISTENCIA (Scan Count)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        scanCount = prefs.getInt("scan_count", 0)

        // 4. ACTUALIZAR INTERFAZ INICIAL
        actualizarUIPlan()

        // 5. 🔥 CARGAR MEMORIA DE INGREDIENTES (Recuperación de Smart Kitchen)
        // Se asume que MemoryManager es un Object/Singleton ya definido en tu proyecto
        try {
            val guardados = MemoryManager.obtener(this)
            if (guardados.isNotEmpty()) {
                ingredientesDetectados.clear()
                ingredientesDetectados.addAll(guardados)
                mostrarOpciones()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. LISTENERS DE EVENTOS
        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                actualizarUIPlan()
                abrirCamara()
            } else {
                mostrarUpgrade()
            }
        }

        btnCart.setOnClickListener {
            // Navegación al módulo de Carrito fusionado
            val intent = Intent(this, CartActivity::class.java)
            startActivity(intent)
        }

        // 7. EJECUCIÓN DE LÓGICA DE FRESCURA (Exclusivo Smart Kitchen)
        evaluarFrescuraDemo()
    }

    private fun actualizarUIPlan() {
        val limite = when(userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }
        txtPlan.text = "Plan: $userPlan | Escaneos: $scanCount / $limite"
    }

    private fun puedeEscanear(): Boolean {
        val limite = when(userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }

        return if (scanCount < limite) {
            scanCount++

            // 🔥 GUARDAR PROGRESO DE ESCANEO
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("scan_count", scanCount).apply()

            true
        } else {
            false
        }
    }

    private fun abrirCamara() {
        // Validación de seguridad para cámaras en Android 11+
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, 100)
        } else {
            Toast.makeText(this, "No se encontró aplicación de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Aquí es donde Smart Kitchen procesaría la imagen con IA
            procesarImagen()
        }
    }

    private fun procesarImagen() {
        // Simulando detección de IA para Smart Kitchen
        ingredientesDetectados.clear()
        ingredientesDetectados.addAll(listOf("huevo", "tocino", "cebolla"))

        // Persistir los nuevos ingredientes detectados
        MemoryManager.guardar(this, ingredientesDetectados)

        mostrarOpciones()
    }

    private fun mostrarOpciones() {
        // Limpiar contenedor para evitar duplicados al refrescar
        chipContainer.removeAllViews()

        // El RecipeEngine decide qué cocinar basado en lo que hay en la Smart Kitchen
        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        opciones.forEach { receta ->

            // Creación dinámica de "Chips" de recetas con diseño mejorado
            val chip = TextView(this)
            chip.text = receta.uppercase()
            chip.setTextColor(Color.WHITE)
            chip.textSize = 14f
            chip.setPadding(45, 25, 45, 25)
            chip.gravity = Gravity.CENTER

            // Diseño visual del botón (Naranja Smart Kitchen)
            val shape = GradientDrawable()
            shape.cornerRadius = 75f
            shape.setColor(Color.parseColor("#FF5722")) // Color vibrante para UI
            shape.setStroke(2, Color.parseColor("#E64A19"))

            chip.background = shape

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(18, 18, 18, 18)
            chip.layoutParams = params

            // Lógica de click: Detección inteligente de faltantes
            chip.setOnClickListener {

                val faltantes = SmartCartManager.detectarFaltantes(
                    receta,
                    ingredientesDetectados
                )

                if (faltantes.isNotEmpty()) {
                    val mensaje = "🥗 Para esta receta te falta: ${faltantes.joinToString(", ")}\n\n" +
                                 "¿Deseas agregarlos a tu lista de compras?"

                    AlertDialog.Builder(this)
                        .setTitle("🛒 Smart Cart: Faltantes")
                        .setMessage(mensaje)
                        .setPositiveButton("Agregar") { _, _ ->
                            // Integración con la memoria del carrito
                            CartMemory.agregarLista(this, faltantes)
                            Toast.makeText(this, "Añadido a Smart Kitchen Cart", Toast.LENGTH_SHORT).show()
                            
                            val intent = Intent(this, CartActivity::class.java)
                            startActivity(intent)
                        }
                        .setNegativeButton("Solo Ver Receta") { _, _ ->
                            abrirReceta(receta)
                        }
                        .setNeutralButton("Ver Tip") { _, _ ->
                            mostrarTip()
                        }
                        .show()

                } else {
                    // Si tienes todo, directo a cocinar
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
            .setTitle("🚀 Smart Kitchen Premium")
            .setMessage("Has agotado tus escaneos gratuitos.\n\nDesbloquea escaneos ilimitados, detección de frescura avanzada y recetas exclusivas.")
            .setPositiveButton("Ver Planes") { _, _ ->
                // Espacio para lógica de facturación futura
                Toast.makeText(this, "Módulo de pagos próximamente", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luego", null)
            .show()
    }

    private fun mostrarTip() {
        // Simulación de notificaciones inteligentes
        Toast.makeText(
            this,
            "💡 Smart Tip: Las espinacas duran 3 días más si las guardas con papel absorbente.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun evaluarFrescuraDemo() {
        // Función distintiva de Smart Kitchen: Semáforo de Frescura
        val mensaje = StringBuilder()
        mensaje.append("🚨 ¡ATENCIÓN!\n\n")
        mensaje.append("🔴 Consumir hoy: Espinacas (Riesgo de marchitez)\n")
        mensaje.append("🟡 Usar pronto: Tomates (Maduración avanzada)\n")
        mensaje.append("🟢 Estado óptimo: Cebollas y Huevos\n\n")
        mensaje.append("💡 Tip Pro: Compra aguacates hoy, estarán listos para el viernes.")

        AlertDialog.Builder(this)
            .setTitle("📊 Estado de tu Smart Kitchen")
            .setMessage(mensaje.toString())
            .setPositiveButton("Entendido", null)
            .show()
    }

    // 🔥 Gestión de memoria del AdView para evitar fugas de memoria (Memory Leaks)
    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        super.onDestroy()
    }
}
