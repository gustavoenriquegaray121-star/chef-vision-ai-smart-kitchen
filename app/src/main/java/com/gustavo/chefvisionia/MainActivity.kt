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

    // ─── ESTADO ───────────────────────────────────────────────────────────────
    private var scanCount = 0
    private var userPlan = "GRATUITO"
    private val ingredientesDetectados = mutableListOf<String>()

    // ─── VISTAS ───────────────────────────────────────────────────────────────
    private lateinit var chipContainer: LinearLayout
    private lateinit var txtPlan: TextView
    private lateinit var txtTip: TextView
    private lateinit var txtEvento: TextView
    private lateinit var btnScan: Button
    private lateinit var adView: AdView
    private lateinit var progressBar: ProgressBar

    // ─── CÁMARA ───────────────────────────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                procesarImagen(bitmap)
            } else {
                Toast.makeText(
                    this,
                    "⚠️ No se pudo capturar la imagen. Intenta de nuevo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ─── CICLO DE VIDA ────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inicializarVistas()
        inicializarAdMob()
        inicializarDatos()
        inicializarUI()
        inicializarListeners()
    }

    override fun onResume() {
        super.onResume()
        // Refrescar tip y semáforo cada vez que el usuario regresa
        mostrarTipPorHora()
        verificarEventoFamiliar()
    }

    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroy()
    }

    // ─── INICIALIZACIÓN ───────────────────────────────────────────────────────

    private fun inicializarVistas() {
        chipContainer = findViewById(R.id.chipContainer)
        txtPlan      = findViewById(R.id.txtPlan)
        txtTip       = findViewById(R.id.txtTip)
        txtEvento    = findViewById(R.id.txtEvento)
        btnScan      = findViewById(R.id.btnScanIngredients)
        adView       = findViewById(R.id.adView)
        progressBar  = findViewById(R.id.progressBar)
    }

    private fun inicializarAdMob() {
        try {
            MobileAds.initialize(this) {}
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun inicializarDatos() {
        GeminiEngine.apiKey = BuildConfig.GEMINI_API_KEY
        EventMemoryManager.initFamilia(this)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        scanCount = prefs.getInt("scan_count", 0)

        // Cargar ingredientes guardados en memoria
        val guardados = MemoryManager.obtener(this)
        if (guardados.isNotEmpty()) {
            ingredientesDetectados.addAll(guardados)
        }
    }

    private fun inicializarUI() {
        actualizarUIPlan()
        mostrarTipPorHora()
        verificarEventoFamiliar()
        evaluarYMostrarSemaforo()

        // Mostrar opciones previas si hay ingredientes en memoria
        if (ingredientesDetectados.isNotEmpty()) {
            mostrarOpciones()
        }

        // Animación pulsante en el botón Scan
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        btnScan.startAnimation(pulse)
    }

    private fun inicializarListeners() {
        // Truco desarrollador: mantener presionado el plan
        txtPlan.setOnLongClickListener {
            scanCount = 0
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putInt("scan_count", 0).apply()
            actualizarUIPlan()
            Toast.makeText(this, "🚀 Modo Dev: Escaneos reseteados", Toast.LENGTH_LONG).show()
            true
        }

        btnScan.setOnClickListener {
            if (puedeEscanear()) abrirCamara() else mostrarUpgrade()
        }

        findViewById<View>(R.id.btnGoToCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    // ─── LÓGICA DE PLAN ───────────────────────────────────────────────────────

    private fun actualizarUIPlan() {
        val limite = limiteDeEscaneos()
        val textoLimite = if (limite >= 99999) "∞" else limite.toString()
        txtPlan.text = "Plan: $userPlan | Escaneos: $scanCount / $textoLimite"
    }

    private fun limiteDeEscaneos(): Int = when (userPlan) {
        "PREMIUM" -> 20
        "SUPER"   -> 99999
        else      -> 3
    }

    private fun puedeEscanear(): Boolean {
        val limite = limiteDeEscaneos()
        return if (scanCount < limite) {
            scanCount++
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putInt("scan_count", scanCount).apply()
            actualizarUIPlan()
            true
        } else false
    }

    // ─── EVENTOS FAMILIARES ───────────────────────────────────────────────────

    private fun verificarEventoFamiliar() {
        val evento = EventMemoryManager.buscarEventoCercano(this) ?: return
        val mensaje = EventMemoryManager.obtenerMensajeEvento(evento)
        if (mensaje.isNotEmpty()) {
            txtEvento.visibility = View.VISIBLE
            txtEvento.text = mensaje
            btnScan.text = "🎂 SCAN"
        }
    }

    // ─── TIP POR HORA ─────────────────────────────────────────────────────────

    private fun mostrarTipPorHora() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        txtTip.text = when {
            hora < 6  -> "🌙 Madrugada — todo está tranquilo, ¿planeas el desayuno?"
            hora < 11 -> "🌅 Buenos días — ¿Qué hay en tu refri para el desayuno?"
            hora < 15 -> "☀️ Hora de la comida — escanea y te sugiero algo rico"
            hora < 19 -> "🌆 Tarde — ¿Ya pensaste qué vas a cenar?"
            hora < 22 -> "🌙 Noche — ideal para planear el desayuno de mañana"
            else      -> "🌙 Ya es tarde — descansa, mañana cocinamos algo especial"
        }
    }

    // ─── SEMÁFORO DE FRESCURA ─────────────────────────────────────────────────

    private fun evaluarYMostrarSemaforo() {
        val inventario = MemoryManager.obtener(this)
        if (inventario.isEmpty()) return

        val prefs = getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()

        val rojos    = mutableListOf<Pair<String, String>>() // ingrediente + sugerencia
        val amarillos = mutableListOf<String>()
        val verdes   = mutableListOf<String>()

        inventario.forEach { ingrediente ->
            val key = ingrediente.lowercase()
            val fechaCarga = prefs.getLong(key, 0L)

            val diasPasados = if (fechaCarga != 0L)
                TimeUnit.MILLISECONDS.toDays(ahora - fechaCarga).toInt()
            else 0

            val limite = FreshnessManager.freshnessRules.entries
                .find { key.contains(it.key) }?.value ?: 7

            when {
                diasPasados >= limite -> {
                    val sugerencia = FreshnessManager.sugerencias.entries
                        .find { key.contains(it.key) }?.value
                        ?: "¡Cocina algo rico antes de que se pierda!"
                    rojos.add(Pair(ingrediente, sugerencia))
                }
                diasPasados >= limite - 2 -> amarillos.add(ingrediente)
                else -> verdes.add(ingrediente)
            }
        }

        if (rojos.isEmpty() && amarillos.isEmpty()) return

        val sb = StringBuilder()
        sb.appendLine("📊 Estado de tu Smart Kitchen\n")

        if (rojos.isNotEmpty()) {
            sb.appendLine("🔴 ¡Usa HOY!: ${rojos.map { it.first }.joinToString(", ")}")
            rojos.forEach { (_, sugerencia) ->
                sb.appendLine("   💡 $sugerencia")
            }
            sb.appendLine()
        }

        if (amarillos.isNotEmpty()) {
            sb.appendLine("🟡 Usar pronto (2-3 días): ${amarillos.joinToString(", ")}\n")
        }

        if (verdes.isNotEmpty()) {
            sb.appendLine("🟢 Fresco y bien: ${verdes.joinToString(", ")}\n")
        }

        sb.append("🛒 Tip: Martes y jueves — frutas y verduras 20% off en Soriana")

        AlertDialog.Builder(this)
            .setTitle("🚨 Alerta Desperdicio Cero")
            .setMessage(sb.toString())
            .setPositiveButton("👨‍🍳 Cocinar con esto") { _, _ ->
                mostrarOpciones()
            }
            .setNeutralButton("🛒 Agregar a lista") { _, _ ->
                val ingredientesCriticos = rojos.map { it.first }
                CartMemory.agregarLista(this, ingredientesCriticos)
                startActivity(Intent(this, CartActivity::class.java))
            }
            .setNegativeButton("Luego", null)
            .show()
    }

    // ─── CÁMARA Y PROCESAMIENTO ───────────────────────────────────────────────

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun procesarImagen(bitmap: Bitmap) {
        txtTip.text = "🔍 Analizando con IA..."
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE
        chipContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val bitmapResized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                val ingredientes = GeminiEngine.detectarIngredientes(bitmapResized)

                if (ingredientes.isNotEmpty()) {
                    ingredientesDetectados.clear()
                    ingredientesDetectados.addAll(ingredientes)
                    MemoryManager.guardar(this@MainActivity, ingredientes)

                    // Guardar timestamp para semáforo de frescura
                    val inventoryPrefs = getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
                    val editor = inventoryPrefs.edit()
                    ingredientes.forEach { editor.putLong(it.lowercase(), System.currentTimeMillis()) }
                    editor.apply()

                    mostrarOpciones()
                } else {
                    // Fallback offline
                    Toast.makeText(
                        this@MainActivity,
                        "⚠️ IA no detectó ingredientes — usando modo offline",
                        Toast.LENGTH_LONG
                    ).show()
                    if (ingredientesDetectados.isEmpty()) {
                        ingredientesDetectados.addAll(listOf("huevo", "cebolla", "tomate"))
                    }
                    mostrarOpciones()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "❌ Error: ${e.localizedMessage ?: "Error desconocido"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                mostrarTipPorHora()
            }
        }
    }

    // ─── OPCIONES DE RECETAS ──────────────────────────────────────────────────

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()

        // Encabezado de ingredientes detectados
        val tvDetectados = TextView(this).apply {
            text = "🥗 Detecté: ${ingredientesDetectados.joinToString(", ")}"
            textSize = 13f
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(16, 8, 16, 16)
            gravity = Gravity.CENTER
        }
        chipContainer.addView(tvDetectados)

        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)

        if (opciones.isEmpty()) {
            val tvVacio = TextView(this).apply {
                text = "😅 No encontré recetas con estos ingredientes.\nIntenta escanear de nuevo."
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
            }
            chipContainer.addView(tvVacio)
            return
        }

        opciones.forEach { receta ->
            val chip = TextView(this).apply {
                text = receta
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(48, 28, 48, 28)
                gravity = Gravity.CENTER

                background = GradientDrawable().apply {
                    cornerRadius = 80f
                    setColor(Color.parseColor("#FF5722"))
                    setStroke(2, Color.parseColor("#E64A19"))
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 12, 16, 12)
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                // Fade in animado
                alpha = 0f
                animate().alpha(1f).setDuration(400).start()
            }

            chip.setOnClickListener {
                manejarSeleccionReceta(receta)
            }

            chipContainer.addView(chip)
        }
    }

    // ─── LÓGICA DE RECETA ─────────────────────────────────────────────────────

    private fun manejarSeleccionReceta(receta: String) {
        val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)

        if (faltantes.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val sustitucion = GeminiEngine.sugerirSustitucion(
                        faltantes.first(),
                        ingredientesDetectados
                    )

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("🛒 Te falta: ${faltantes.joinToString(", ")}")
                        .setMessage(
                            "$sustitucion\n\n" +
                            "¿Qué deseas hacer?"
                        )
                        .setPositiveButton("🛒 Agregar al carrito") { _, _ ->
                            CartMemory.agregarLista(this@MainActivity, faltantes)
                            mostrarOpcionesEntrega(faltantes)
                        }
                        .setNeutralButton("👨‍🍳 Cocinar con lo que tengo") { _, _ ->
                            abrirReceta(receta)
                        }
                        .setNegativeButton("❌ Cancelar", null)
                        .show()

                } catch (e: Exception) {
                    abrirReceta(receta)
                }
            }
        } else {
            abrirReceta(receta)
        }
    }

    private fun abrirReceta(receta: String) {
        val intent = Intent(this, RecipeActivity::class.java).apply {
            putExtra("RECETA", receta)
            putStringArrayListExtra("INGREDIENTES", ArrayList(ingredientesDetectados))
        }
        startActivity(intent)
    }

    // ─── OPCIONES DE ENTREGA ──────────────────────────────────────────────────

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

    // ─── UPGRADE ──────────────────────────────────────────────────────────────

    private fun mostrarUpgrade() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Desbloquea Chef Vision")
            .setMessage(
                "Has agotado tus escaneos gratuitos de hoy.\n\n" +
                "⭐ PREMIUM \$699/año:\n" +
                "• 20 escaneos diarios\n" +
                "• Todas las cocinas internacionales\n" +
                "• Sin anuncios\n\n" +
                "👑 PLAN EMBAJADOR \$899/año:\n" +
                "• Escaneos ILIMITADOS\n" +
                "• Memoria familiar 💖\n" +
                "• Recordatorios de cumpleaños\n" +
                "• Fitness, Vegano y Maridaje\n" +
                "• Integración Rappi / Uber Eats\n" +
                "• Notificaciones Zero Waste"
            )
            .setPositiveButton("👑 Plan Embajador") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("⭐ Premium") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luego", null)
            .show()
    }
}
