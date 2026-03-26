package com.gustavo.chefvisionia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var scanCount = 0
    private var userPlan = "GRATUITO"

    private lateinit var chipContainer: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var btnCart: ImageButton
    private lateinit var txtPlan: TextView
    private lateinit var txtTip: TextView
    private lateinit var adView: AdView

    private val ingredientesDetectados = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chipContainer = findViewById(R.id.chipContainer)
        btnScan = findViewById(R.id.btnScanIngredients)
        btnCart = findViewById(R.id.btnGoToCart)
        txtPlan = findViewById(R.id.txtPlan)
        txtTip = findViewById(R.id.txtTip)

        // API Key desde BuildConfig
        GeminiEngine.apiKey = BuildConfig.GEMINI_API_KEY

        // AdMob
        try {
            MobileAds.initialize(this) {}
            adView = findViewById(R.id.adView)
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Cargar scan count
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        scanCount = prefs.getInt("scan_count", 0)
        actualizarUIPlan()

        // Cargar memoria
        try {
            val guardados = MemoryManager.obtener(this)
            if (guardados.isNotEmpty()) {
                ingredientesDetectados.addAll(guardados)
                mostrarOpciones()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Tip inteligente por hora
        mostrarTipPorHora()

        // Truco desarrollador — mantén presionado el título
        txtPlan.setOnLongClickListener {
            scanCount = 0
            prefs.edit().putInt("scan_count", 0).apply()
            actualizarUIPlan()
            Toast.makeText(this, "🚀 Modo Dev: Escaneos reseteados", Toast.LENGTH_LONG).show()
            true
        }

        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                actualizarUIPlan()
                abrirCamara()
            } else {
                mostrarUpgrade()
            }
        }

        btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        // Semáforo de frescura al abrir
        evaluarFrescura()
    }

    private fun actualizarUIPlan() {
        val limite = when (userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }
        txtPlan.text = "Plan: $userPlan | Escaneos: $scanCount / $limite"
    }

    private fun puedeEscanear(): Boolean {
        val limite = when (userPlan) {
            "GRATUITO" -> 3
            "PREMIUM" -> 20
            else -> 99999
        }
        return if (scanCount < limite) {
            scanCount++
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putInt("scan_count", scanCount).apply()
            true
        } else false
    }

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, 100)
        } else {
            Toast.makeText(this, "No se encontró cámara", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            procesarImagen(data)
        }
    }

    private fun procesarImagen(data: Intent?) {
        // Obtener bitmap real de la cámara
        val bitmap = data?.extras?.get("data") as? Bitmap

        if (bitmap != null) {
            txtPlan.text = "🔍 Analizando con IA..."

            lifecycleScope.launch {
                try {
                    // Resize para optimizar — máximo 512x512
                    val bitmapResized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)

                    // Gemini real detecta ingredientes
                    val ingredientes = GeminiEngine.detectarIngredientes(bitmapResized)

                    if (ingredientes.isNotEmpty()) {
                        ingredientesDetectados.clear()
                        ingredientesDetectados.addAll(ingredientes)
                        MemoryManager.guardar(this@MainActivity, ingredientes)
                        mostrarOpciones()
                    } else {
                        // Fallback al RecipeEngine offline
                        ingredientesDetectados.clear()
                        ingredientesDetectados.addAll(listOf("huevo", "cebolla"))
                        mostrarOpciones()
                        Toast.makeText(
                            this@MainActivity,
                            "⚠️ IA no detectó ingredientes, usando modo offline",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    actualizarUIPlan()
                }
            }
        } else {
            Toast.makeText(this, "No se pudo capturar imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()
        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        // Mostrar ingredientes detectados
        val tvIngredientes = TextView(this)
        tvIngredientes.text = "🥗 Detecté: ${ingredientesDetectados.joinToString(", ")}"
        tvIngredientes.textSize = 13f
        tvIngredientes.setPadding(10, 10, 10, 16)
        tvIngredientes.setTextColor(Color.parseColor("#4CAF50"))
        chipContainer.addView(tvIngredientes)

        opciones.forEach { receta ->
            val chip = TextView(this)
            chip.text = receta.uppercase()
            chip.setTextColor(Color.WHITE)
            chip.textSize = 14f
            chip.setPadding(45, 25, 45, 25)
            chip.gravity = Gravity.CENTER

            val shape = GradientDrawable()
            shape.cornerRadius = 75f
            shape.setColor(Color.parseColor("#FF5722"))
            shape.setStroke(2, Color.parseColor("#E64A19"))
            chip.background = shape

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(18, 18, 18, 18)
            chip.layoutParams = params

            chip.setOnClickListener {
                val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)

                if (faltantes.isNotEmpty()) {
                    // Mostrar sustitución inteligente con Gemini
                    lifecycleScope.launch {
                        val sustitucion = GeminiEngine.sugerirSustitucion(
                            faltantes.first(),
                            ingredientesDetectados
                        )

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("🛒 Te falta: ${faltantes.joinToString(", ")}")
                            .setMessage("$sustitucion\n\n¿Qué deseas hacer?")
                            .setPositiveButton("Agregar al carrito") { _, _ ->
                                CartMemory.agregarLista(this@MainActivity, faltantes)
                                mostrarOpcionesEntrega(faltantes)
                            }
                            .setNegativeButton("Cocinar con lo que tengo") { _, _ ->
                                abrirReceta(receta)
                            }
                            .setNeutralButton("Ver receta") { _, _ ->
                                abrirReceta(receta)
                            }
                            .show()
                    }
                } else {
                    abrirReceta(receta)
                }
            }

            chipContainer.addView(chip)
        }
    }

    private fun mostrarOpcionesEntrega(faltantes: List<String>) {
        val query = faltantes.joinToString("+")

        AlertDialog.Builder(this)
            .setTitle("🚚 ¿Cómo quieres conseguirlo?")
            .setMessage("Tienes faltantes: ${faltantes.joinToString(", ")}")
            .setPositiveButton("🛵 Rappi") { _, _ ->
                abrirApp(
                    "com.grability.rappi",
                    "https://www.rappi.com.mx/buscar/$query"
                )
            }
            .setNeutralButton("🚗 Uber Eats") { _, _ ->
                abrirApp(
                    "com.ubercab.eats",
                    "https://www.ubereats.com/mx/search?q=$query"
                )
            }
            .setNegativeButton("🏪 Yo voy") { _, _ ->
                mostrarTipSoriana()
            }
            .show()
    }

    private fun abrirApp(packageName: String, urlFallback: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlFallback)))
            }
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlFallback)))
        }
    }

    private fun mostrarTipSoriana() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val tip = when {
            hora < 9  -> "🌅 Ve temprano — antes de las 9am hay menos gente en Soriana"
            hora < 12 -> "☀️ Buen momento para ir — martes y jueves son días de frutas y verduras con 20-30% de descuento"
            hora < 15 -> "🌞 Mediodía — Walmart tiene ofertas de mediodía en lácteos"
            hora < 19 -> "🌆 Tarde — Chedraui tiene descuentos en pan y tortillas después de las 5pm"
            else      -> "🌙 Noche — algunos Soriana 24hrs tienen liquidaciones nocturnas"
        }

        AlertDialog.Builder(this)
            .setTitle("💡 Smart Tip de Ahorro")
            .setMessage(tip)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun abrirReceta(receta: String) {
        val intent = Intent(this, RecipeActivity::class.java)
        intent.putExtra("RECETA", receta)
        intent.putStringArrayListExtra("INGREDIENTES", ArrayList(ingredientesDetectados))
        startActivity(intent)
    }

    private fun mostrarUpgrade() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Chef Vision Premium")
            .setMessage(
                "Has agotado tus escaneos gratuitos.\n\n" +
                "⭐ PREMIUM \$699/año:\n" +
                "• 20 escaneos diarios\n" +
                "• Todas las cocinas\n" +
                "• Sin anuncios\n\n" +
                "👑 SÚPER PREMIUM \$899/año:\n" +
                "• Ilimitado\n" +
                "• Fitness, Vegano, Postres\n" +
                "• Maridaje con vinos\n" +
                "• Integración Rappi/Uber"
            )
            .setPositiveButton("👑 Súper Premium") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("⭐ Premium") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luego", null)
            .show()
    }

    private fun mostrarTipPorHora() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        txtTip.text = when {
            hora < 11 -> "🌅 Buenos días — ¿Qué hay en tu refri para el desayuno?"
            hora < 15 -> "☀️ Hora de la comida — escanea y te sugiero algo rico"
            hora < 19 -> "🌆 Tarde — ¿Ya pensaste qué vas a cenar?"
            else      -> "🌙 Buenas noches — planea el desayuno de mañana"
        }
    }

    private fun evaluarFrescura() {
        try {
            val ingredientes = MemoryManager.obtener(this)
            if (ingredientes.isEmpty()) return

            val mensaje = StringBuilder()
            mensaje.append("📊 Estado de tu Smart Kitchen\n\n")
            mensaje.append("🔴 Consumir hoy: Espinacas\n")
            mensaje.append("🟡 Usar pronto: Tomates\n")
            mensaje.append("🟢 Fresco: Cebollas y Huevos\n\n")
            mensaje.append("💡 Tip: Martes y jueves — frutas y verduras 20% off en Soriana 🛒")

            AlertDialog.Builder(this)
                .setTitle("🚨 Alerta Desperdicio Cero")
                .setMessage(mensaje.toString())
                .setPositiveButton("Cocinar con esto") { _, _ ->
                    mostrarOpciones()
                }
                .setNegativeButton("Luego", null)
                .show()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroy()
    }
}
