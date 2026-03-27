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

    // Cámara moderna con RegisterForActivityResult (Punto 9)
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

        // Inicializar Vistas
        chipContainer = findViewById(R.id.chipContainer)
        txtPlan = findViewById(R.id.txtPlan)
        txtTip = findViewById(R.id.txtTip)
        txtEvento = findViewById(R.id.txtEvento)
        btnScan = findViewById(R.id.btnScanIngredients)
        adView = findViewById(R.id.adView)

        // Configuración de Motor y Ads
        GeminiEngine.apiKey = BuildConfig.GEMINI_API_KEY
        try {
            MobileAds.initialize(this) {}
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) { e.printStackTrace() }

        // Vínculo Familiar (Puntos 18-21)
        EventMemoryManager.initFamilia(this)

        // Cargar Estado de Usuario y Plan
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        scanCount = prefs.getInt("scan_count", 0)
        actualizarUIPlan()

        // Cargar Ingredientes de Memoria
        val guardados = MemoryManager.obtener(this)
        if (guardados.isNotEmpty()) {
            ingredientesDetectados.addAll(guardados)
            mostrarOpciones()
        }

        // Tips y Eventos (Punto 14 y 20)
        mostrarTipPorHora()
        verificarEventoFamiliar()

        // Animación Pulsante (Punto 3 y 33)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        btnScan.startAnimation(pulse)

        // Evaluar Semáforo de Frescura al abrir (Punto 11)
        ejecutarAlertaFrescura()

        // Listener de Escaneo
        btnScan.setOnClickListener {
            if (puedeEscanear()) {
                abrirCamara()
            } else {
                mostrarUpgrade()
            }
        }

        // Acceso al Carrito
        findViewById<View>(R.id.btnGoToCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        // Truco Dev: Resetear contador
        txtPlan.setOnLongClickListener {
            scanCount = 0
            prefs.edit().putInt("scan_count", 0).apply()
            actualizarUIPlan()
            Toast.makeText(this, "🚀 Dev: Escaneos reseteados", Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun verificarEventoFamiliar() {
        val evento = EventMemoryManager.buscarEventoCercano(this)
        if (evento != null) {
            val mensaje = EventMemoryManager.obtenerMensajeEvento(evento)
            if (mensaje.isNotEmpty()) {
                txtEvento.visibility = View.VISIBLE
                txtEvento.text = mensaje
                // Punto 22: Icono de pastel en el botón Scan
                btnScan.text = "🎂 SCAN"
            }
        }
    }

    private fun ejecutarAlertaFrescura() {
        val alerta = FreshnessManager.evaluarFrescura(this)
        if (alerta.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("🚨 Alerta Desperdicio Cero")
                .setMessage(alerta)
                .setPositiveButton("Cocinar ahora", null)
                .show()
        }
    }

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun procesarImagen(bitmap: Bitmap) {
        txtTip.text = "🔍 Analizando con IA..."
        btnScan.isEnabled = false

        lifecycleScope.launch {
            try {
                val bitmapResized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
                val ingredientes = GeminiEngine.detectarIngredientes(bitmapResized)

                if (ingredientes.isNotEmpty()) {
                    ingredientesDetectados.clear()
                    ingredientesDetectados.addAll(ingredientes)
                    MemoryManager.guardar(this@MainActivity, ingredientes)
                    mostrarOpciones()
                } else {
                    Toast.makeText(this@MainActivity, "⚠️ IA no detectó, modo offline", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnScan.isEnabled = true
                actualizarUIPlan()
                mostrarTipPorHora()
            }
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

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()
        val tvDetectados = TextView(this).apply {
            text = "🥗 Detecté: ${ingredientesDetectados.joinToString(", ")}"
            textSize = 13f
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(16, 8, 16, 16)
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
                background = GradientDrawable().apply {
                    cornerRadius = 80f
                    setColor(Color.parseColor("#FF5722"))
                    setStroke(2, Color.parseColor("#E64A19"))
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(16, 12, 16, 12)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            chip.setOnClickListener {
                val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)
                if (faltantes.isNotEmpty()) {
                    lifecycleScope.launch {
                        val sustitucion = GeminiEngine.sugerirSustitucion(faltantes.first(), ingredientesDetectados)
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("🛒 Falta: ${faltantes.joinToString(", ")}")
                            .setMessage("$sustitucion\n\n¿Qué deseas hacer?")
                            .setPositiveButton("Comprar") { _, _ -> mostrarOpcionesEntrega(faltantes) }
                            .setNegativeButton("Cocinar así") { _, _ -> abrirReceta(receta) }
                            .show()
                    }
                } else { abrirReceta(receta) }
            }
            chipContainer.addView(chip)
        }
    }

    private fun mostrarOpcionesEntrega(faltantes: List<String>) {
        val query = faltantes.joinToString("+")
        AlertDialog.Builder(this)
            .setTitle("🚚 ¿Cómo lo conseguimos?")
            .setPositiveButton("Rappi") { _, _ -> abrirApp("com.grability.rappi", "https://rappi.mx/buscar/$query") }
            .setNeutralButton("Uber Eats") { _, _ -> abrirApp("com.ubercab.eats", "https://ubereats.com/search?q=$query") }
            .setNegativeButton("Tienda") { _, _ -> mostrarTipTienda() }
            .show()
    }

    private fun abrirApp(pkg: String, url: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        startActivity(intent ?: Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun mostrarTipTienda() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val tip = when {
            hora < 9  -> "🌅 Ve ya: Soriana está vacío antes de las 9am"
            hora < 12 -> "☀️ Martes y Jueves: 20-30% off frutas en Soriana"
            else      -> "🌙 Ofertas nocturnas en Soriana 24hrs"
        }
        AlertDialog.Builder(this).setTitle("💡 Tip").setMessage(tip).show()
    }

    private fun mostrarTipPorHora() {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        txtTip.text = when {
            hora < 11 -> "🌅 Buenos días — ¿Qué desayunamos?"
            hora < 15 -> "☀️ Hora de comida — ¡Escanea!"
            else      -> "🌙 Planifica tu cena o el desayuno de mañana"
        }
    }

    private fun abrirReceta(r: String) {
        val intent = Intent(this, RecipeActivity::class.java).apply {
            putExtra("RECETA", r)
            putStringArrayListExtra("INGREDIENTES", ArrayList(ingredientesDetectados))
        }
        startActivity(intent)
    }

    private fun mostrarUpgrade() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Plan Embajador")
            .setMessage("Escaneos agotados. Únete al Plan Embajador para memoria familiar 💖 y escaneos ilimitados.")
            .setPositiveButton("Saber más", null)
            .show()
    }

    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroy()
    }
}

// --- GESTIÓN DE FRESCURA (FRESHNESS MANAGER) ---
object FreshnessManager {
    private val rules = mapOf("espinaca" to 3, "tomate" to 5, "pollo" to 2, "huevo" to 14, "cebolla" to 10)

    fun evaluarFrescura(context: Context): String {
        val inventario = MemoryManager.obtener(context)
        if (inventario.isEmpty()) return ""
        
        val prefs = context.getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val criticos = mutableListOf<String>()

        inventario.forEach { ing ->
            val fecha = prefs.getLong(ing, 0L)
            if (fecha != 0L) {
                val dias = TimeUnit.MILLISECONDS.toDays(now - fecha).toInt()
                val max = rules.entries.find { ing.contains(it.key) }?.value ?: 7
                if (dias >= max) criticos.add(ing)
            }
        }

        return if (criticos.isNotEmpty()) "🔴 ¡Usa HOY para evitar desperdicio!: ${criticos.joinToString(", ")}" else ""
    }
}
