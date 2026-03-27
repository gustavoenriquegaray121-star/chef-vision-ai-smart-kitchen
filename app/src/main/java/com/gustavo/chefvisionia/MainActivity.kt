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
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var scanCount = 0
    private var userPlan = "GRATUITO"
    private val ingredientesDetectados = mutableListOf<String>()

    private lateinit var chipContainer: LinearLayout
    private lateinit var txtPlan: TextView
    private lateinit var txtTip: TextView
    private lateinit var txtEvento: TextView
    private lateinit var btnScan: Button
    private lateinit var adView: AdView
    private lateinit var progressBar: ProgressBar

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                procesarImagen(bitmap)
            } else {
                Toast.makeText(this, "⚠️ No se pudo capturar imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chipContainer = findViewById(R.id.chipContainer)
        txtPlan = findViewById(R.id.txtPlan)
        txtTip = findViewById(R.id.txtTip)
        txtEvento = findViewById(R.id.txtEvento)
        btnScan = findViewById(R.id.btnScanIngredients)
        adView = findViewById(R.id.adView)
        progressBar = findViewById(R.id.progressBar)

        GeminiEngine.apiKey = BuildConfig.GEMINI_API_KEY

        try {
            MobileAds.initialize(this)            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) { e.printStackTrace() }

        EventMemoryManager.initFamilia(this)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        scanCount = prefs.getInt("scan_count", 0)
        actualizarUIPlan()

        val guardados = MemoryManager.obtener(this)
        if (guardados.isNotEmpty()) {
            ingredientesDetectados.addAll(guardados)
            mostrarOpciones()
        }

        mostrarTipPorHora()
        verificarEventoFamiliar()
        evaluarFrescura()

        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        btnScan.startAnimation(pulse)

        txtPlan.setOnLongClickListener {
            scanCount = 0
            prefs.edit().putInt("scan_count", 0).apply()
            actualizarUIPlan()
            Toast.makeText(this, "🚀 Dev: Escaneos reseteados", Toast.LENGTH_LONG).show()
            true
        }

        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                abrirCamara()
            } else {
                mostrarUpgrade()
            }
        }

        findViewById<View>(R.id.btnGoToCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun verificarEventoFamiliar() {
        val evento = EventMemoryManager.buscarEventoCercano(this)
        if (evento != null) {
            val mensaje = EventMemoryManager.obtenerMensajeEvento(evento)
            if (mensaje.isNotEmpty()) {
                txtEvento.visibility = View.VISIBLE
                txtEvento.text = mensaje
                btnScan.text = "🎂 SCAN"
            }
        }
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

    private fun actualizarUIPlan() {
        val limite = when (userPlan) {
            "PREMIUM" -> 20
            "SUPER"   -> 99999
            else      -> 3
        }
        txtPlan.text = "Plan: $userPlan | Escaneos: $scanCount / $limite"
    }

    private fun puedeEscanear(): Boolean {
        val limite = when (userPlan) {
            "PREMIUM" -> 20
            "SUPER"   -> 99999
            else      -> 3
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
        cameraLauncher.launch(intent)
    }

    private fun procesarImagen(bitmap: Bitmap) {
        txtTip.text = "🔍 Analizando con IA..."
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val bitmapResized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)

                val eventoTexto = EventMemoryManager.buscarEventoCercano(this@MainActivity)
                    ?.let { "Evento cercano: cumpleaños de ${it.nombre} el \( {it.dia}/ \){it.mes}. Le gusta: ${it.gustos}." }
                    ?: ""

                val ingredientes = GeminiEngine.detectarIngredientes(bitmapResized)

                if (ingredientes.isNotEmpty()) {
                    ingredientesDetectados.clear()
                    ingredientesDetectados.addAll(ingredientes)
                    MemoryManager.guardar(this@MainActivity, ingredientes)

                    // ← Guardado de fecha para semáforo real
                    val inventoryPrefs = getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
                    val editor = inventoryPrefs.edit()
                    ingredientes.forEach { editor.putLong(it.lowercase(), System.currentTimeMillis()) }
                    editor.apply()

                    mostrarOpciones()
                } else {
                    ingredientesDetectados.clear()
                    ingredientesDetectados.addAll(listOf("huevo", "cebolla"))
                    mostrarOpciones()
                    Toast.makeText(this@MainActivity, "⚠️ Modo offline activado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Error: ${e.localizedMessage ?: "desconocido"}", Toast.LENGTH_SHORT).show()
            } finally {
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                mostrarTipPorHora()
                actualizarUIPlan()
            }
        }
    }

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()

        val tvDetectados = TextView(this).apply {
            text = "🥗 Detecté: ${ingredientesDetectados.joinToString(", ")}"
            textSize = 13f
            setPadding(16, 8, 16, 16)
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
        }
        chipContainer.addView(tvDetectados)

        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        opciones.forEach { receta ->
            val chip = TextView(this).apply {
                text = receta
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(48, 28, 48, 28)
                gravity = Gravity.CENTER

                val shape = GradientDrawable().apply {
                    cornerRadius = 80f
                    setColor(Color.parseColor("#FF5722"))
                    setStroke(2, Color.parseColor("#E64A19"))
                }
                background = shape

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 12, 16, 12)
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                alpha = 0f
                animate().alpha(1f).setDuration(400).start()
            }

            chip.setOnClickListener {
                val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)

                if (faltantes.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val sustitucion = GeminiEngine.sugerirSustitucion(faltantes.first(), ingredientesDetectados)
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
                                .show()
                        } catch (e: Exception) {
                            abrirReceta(receta)
                        }
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
            .setTitle("🚚 ¿Cómo consigues lo que falta?")
            .setMessage("Falta: ${faltantes.joinToString(", ")}")
            .setPositiveButton("🛵 Rappi") { _, _ ->
                abrirApp("com.grability.rappi", "https://www.rappi.com.mx/buscar/$query")
            }
            .setNeutralButton("🚗 Uber Eats") { _, _ ->
                abrirApp("com.ubercab.eats", "https://www.ubereats.com/mx/search?q=$query")
            }
            .setNegativeButton("🏪 Yo voy") { _, _ ->
                mostrarTipTienda()
            }
            .show()
    }

    private fun abrirApp(packageName: String, urlFallback: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(intent ?: Intent(Intent.ACTION_VIEW, Uri.parse(urlFallback)))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlFallback)))
        }
    }

    private fun mostrarTipTienda() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val tip = when {
            hora < 9  -> "🌅 Ve temprano — antes de las 9am hay menos gente en Soriana"
            hora < 12 -> "☀️ Martes y jueves: frutas y verduras 20-30% off en Soriana"
            hora < 19 -> "🌆 Chedraui tiene descuentos en lácteos después de las 5pm"
            else      -> "🌙 Algunos Soriana 24hrs tienen liquidaciones nocturnas"
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
            .setTitle("🚀 Desbloquea Chef Vision")
            .setMessage(
                "Has agotado tus escaneos gratuitos.\n\n" +
                "⭐ **PREMIUM** — \$699/año\n" +
                "• 20 escaneos diarios\n" +
                "• Todas las cocinas del mundo\n" +
                "• Sin anuncios\n" +
                "• Sugerencias personalizadas\n\n" +
                "👑 **EMBAJADOR** — \$899/año\n" +
                "• Escaneos ilimitados\n" +
                "• Memoria familiar completa 💖\n" +
                "• Recordatorios de cumpleaños\n" +
                "• Planes Fitness, Vegano, Postres y Maridaje\n" +
                "• Integración Rappi/Uber Eats"
            )
            .setPositiveButton("👑 Quiero ser Embajador") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("⭐ Premium") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luego", null)
            .show()
    }

    private fun evaluarFrescura() {
        try {
            val mensaje = FreshnessManager.evaluarFrescura(this)
            if (mensaje.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("🚨 Alerta Desperdicio Cero")
                    .setMessage(mensaje)
                    .setPositiveButton("Cocinar con esto") { _, _ ->
                        mostrarOpciones()
                    }
                    .setNegativeButton("Luego", null)
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    object FreshnessManager {
        private val freshnessRules = mapOf(
            "espinaca" to 3, "lechuga" to 4, "tomate" to 5, "jitomate" to 5,
            "aguacate" to 3, "plátano" to 4, "fresa" to 3, "cilantro" to 4,
            "pollo" to 2, "carne" to 2, "pescado" to 1, "camarón" to 1,
            "leche" to 3, "crema" to 5, "zanahoria" to 7, "pepino" to 5,
            "cebolla" to 10, "ajo" to 14, "queso" to 7, "huevo" to 14,
            "papa" to 10, "limón" to 10, "naranja" to 7, "manzana" to 7,
            "chile" to 7, "brócoli" to 5
        )

        private val sugerencias = mapOf(
            "espinaca" to "¿Un licuado verde, quesadillas o pasta con espinaca? 🌿",
            "tomate" to "¿Unas entomatadas, salsa roja o sopa de tomate? 🍅",
            "jitomate" to "¿Pico de gallo, pizza casera o salsa fresca? 🍅",
            "aguacate" to "¿Guacamole, tostadas o tacos con aguacate? 🥑",
            "plátano" to "¿Plátanos fritos, licuado o pan de plátano? 🍌",
            "pollo" to "¿Pollo al ajillo, caldo tlalpeño o tacos? 🍗",
            "carne" to "¿Bistec a la mexicana, arrachera o picadillo? 🥩",
            "pescado" to "¡Úsalo hoy! ¿Veracruzana o tacos de pescado? 🐟",
            "leche" to "¿Un licuado, arroz con leche o avena? 🥛",
            "huevo" to "¿Unos huevos divorciados o un omelette? 🍳",
            "cebolla" to "¿Sopa de cebolla o aros de cebolla crujientes? 🧅"
        )

        fun evaluarFrescura(context: Context): String {
            val inventario = MemoryManager.obtener(context)
            if (inventario.isEmpty()) return ""
            
            val prefs = context.getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
            val ahora = System.currentTimeMillis()
            val criticos = mutableListOf<String>()

            for (item in inventario) {
                val fechaCarga = prefs.getLong(item, 0L)
                if (fechaCarga != 0L) {
                    val diasPasados = TimeUnit.MILLISECONDS.toDays(ahora - fechaCarga)
                    val limite = freshnessRules ?: 7
                    if (diasPasados >= limite) criticos.add(item)
                }
            }

            return if (criticos.isNotEmpty()) {
                val lista = criticos.joinToString(", ")
                val primera = criticos.first().lowercase()
                val sugerencia = sugerencias ?: "¡Cocina algo rico antes de que se pierdan!"
                "🔴 Urgente usar: $lista\n\n💡 Sugerencia: $sugerencia"
            } else ""
        }
    }

    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroy()
    }
}
