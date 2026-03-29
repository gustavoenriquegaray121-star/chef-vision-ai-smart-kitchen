package com.gustavo.chefvisionia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // ─── ESTADO ───────────────────────────────────────────────────────────────
    private var scanCount = 0
    private var userPlan = "GRATUITO"
    private var cocinaSeleccionada = "Mexicana"
    private val ingredientesDetectados = mutableListOf<String>()
    private var fotoUri: Uri? = null

    // ─── VISTAS ───────────────────────────────────────────────────────────────
    private lateinit var chipContainer: LinearLayout
    private lateinit var txtPlan: TextView
    private lateinit var txtTip: TextView
    private lateinit var txtEvento: TextView
    private lateinit var btnScan: Button
    private lateinit var adView: AdView
    private lateinit var progressBar: ProgressBar

    // ─── SEMÁFORO DE FRESCURA ─────────────────────────────────────────────────
    private object FreshnessManager {
        val freshnessRules = mapOf(
            "espinaca"  to 3,  "lechuga"   to 4,
            "tomate"    to 5,  "jitomate"  to 5,
            "aguacate"  to 3,  "plátano"   to 4,
            "fresa"     to 3,  "cilantro"  to 4,
            "pollo"     to 2,  "carne"     to 2,
            "pescado"   to 1,  "camarón"   to 1,
            "leche"     to 3,  "crema"     to 5,
            "zanahoria" to 7,  "pepino"    to 5,
            "cebolla"   to 10, "ajo"       to 14,
            "queso"     to 7,  "huevo"     to 14,
            "papa"      to 10, "limón"     to 10,
            "naranja"   to 7,  "manzana"   to 7,
            "chile"     to 7,  "brócoli"   to 5
        )

        val sugerencias = mapOf(
            "espinaca"  to "¿Un licuado verde, quesadillas o pasta con espinaca? 🌿",
            "tomate"    to "¿Unas entomatadas, salsa roja o sopa de tomate? 🍅",
            "jitomate"  to "¿Pico de gallo, pizza casera o salsa fresca? 🍅",
            "aguacate"  to "¿Guacamole, tostadas o tacos con aguacate? 🥑",
            "plátano"   to "¿Plátanos fritos, licuado o pan de plátano? 🍌",
            "pollo"     to "¿Pollo al ajillo, caldo tlalpeño o tacos? 🍗",
            "carne"     to "¿Bistec a la mexicana, arrachera o picadillo? 🥩",
            "pescado"   to "¡Úsalo hoy! ¿Veracruzana o tacos de pescado? 🐟",
            "leche"     to "¿Un licuado, arroz con leche o avena? 🥛",
            "huevo"     to "¿Unos huevos divorciados o un omelette? 🍳",
            "cebolla"   to "¿Sopa de cebolla o aros de cebolla crujientes? 🧅"
        )
    }

    // ─── PERMISO CÁMARA ───────────────────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            abrirCamara()
        } else {
            AlertDialog.Builder(this)
                .setTitle("📸 Permiso de cámara necesario")
                .setMessage(
                    "Chef Vision IA necesita acceso a tu cámara para identificar " +
                    "ingredientes y sugerirte recetas.\n\n" +
                    "Por favor actívalo en Ajustes → Aplicaciones → Chef Vision IA → Permisos."
                )
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ─── CÁMARA CON FILEPROVIDER ──────────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = fotoUri
            if (uri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder
                            .createSource(contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.setTargetSampleSize(2)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                    procesarImagen(bitmap)
                } catch (e: Exception) {
                    val bitmap = result.data?.extras?.get("data") as? Bitmap
                    if (bitmap != null) procesarImagen(bitmap)
                    else Toast.makeText(this, "⚠️ Error leyendo imagen", Toast.LENGTH_LONG).show()
                }
            } else {
                val bitmap = result.data?.extras?.get("data") as? Bitmap
                if (bitmap != null) procesarImagen(bitmap)
                else Toast.makeText(this, "⚠️ No se pudo capturar imagen", Toast.LENGTH_LONG).show()
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
        inicializarChipsCocinas()
    }

    override fun onResume() {
        super.onResume()
        mostrarTipPorHora()
        verificarEventoFamiliar()
        evaluarYMostrarSemaforo()
    }

    override fun onDestroy() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroy()
    }

    // ─── INICIALIZACIÓN ───────────────────────────────────────────────────────

    private fun inicializarVistas() {
        chipContainer = findViewById(R.id.chipContainer)
        txtPlan       = findViewById(R.id.txtPlan)
        txtTip        = findViewById(R.id.txtTip)
        txtEvento     = findViewById(R.id.txtEvento)
        btnScan       = findViewById(R.id.btnScanIngredients)
        adView        = findViewById(R.id.adView)
        progressBar   = findViewById(R.id.progressBar)
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
        val guardados = MemoryManager.obtener(this)
        if (guardados.isNotEmpty()) ingredientesDetectados.addAll(guardados)
    }

    private fun inicializarUI() {
        actualizarUIPlan()
        mostrarTipPorHora()
        verificarEventoFamiliar()
        evaluarYMostrarSemaforo()
        if (ingredientesDetectados.isNotEmpty()) mostrarOpciones()
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        btnScan.startAnimation(pulse)
    }

    private fun inicializarListeners() {
        txtPlan.setOnLongClickListener {
            scanCount = 0
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putInt("scan_count", 0).apply()
            actualizarUIPlan()
            Toast.makeText(this, "🚀 Modo Dev: Escaneos reseteados", Toast.LENGTH_LONG).show()
            true
        }
        btnScan.setOnClickListener {
            if (puedeEscanear()) solicitarPermisoCamara() else mostrarUpgrade()
        }
        findViewById<View>(R.id.btnGoToCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    // ─── CHIPS DE COCINAS ─────────────────────────────────────────────────────

    private fun inicializarChipsCocinas() {
        val chipsMexicana = findViewById<TextView>(R.id.chipMexicana)
        val chipsItaliana = findViewById<TextView>(R.id.chipItaliana)
        val chipsChina    = findViewById<TextView>(R.id.chipChina)

        chipsMexicana.setOnClickListener { seleccionarCocina("Mexicana", chipsMexicana) }
        chipsItaliana.setOnClickListener { seleccionarCocina("Italiana", chipsItaliana) }
        chipsChina.setOnClickListener    { seleccionarCocina("China", chipsChina) }

        listOf(
            R.id.chipFrancesa     to "Francesa 🇫🇷",
            R.id.chipJaponesa     to "Japonesa 🇯🇵",
            R.id.chipEspanola     to "Española 🇪🇸",
            R.id.chipAmericana    to "Americana 🇺🇸",
            R.id.chipTailandesa   to "Tailandesa 🇹🇭",
            R.id.chipMediterranea to "Mediterránea 🫒"
        ).forEach { (id, nombre) ->
            findViewById<TextView>(id).setOnClickListener {
                mostrarUpgradeCocina(nombre, "PREMIUM")
            }
        }

        listOf(
            R.id.chipVegana   to "Vegana 🥗",
            R.id.chipFitness  to "Fitness 💪",
            R.id.chipMaridaje to "Maridaje 🍷"
        ).forEach { (id, nombre) ->
            findViewById<TextView>(id).setOnClickListener {
                mostrarUpgradeCocina(nombre, "EMBAJADOR")
            }
        }

        marcarChipSeleccionado(chipsMexicana)
    }

    private fun seleccionarCocina(cocina: String, chip: TextView) {
        cocinaSeleccionada = cocina
        listOf(R.id.chipMexicana, R.id.chipItaliana, R.id.chipChina).forEach { id ->
            findViewById<TextView>(id).apply {
                setBackgroundColor(Color.parseColor("#CC4CAF50"))
                setTextColor(Color.WHITE)
                alpha = 0.7f
            }
        }
        marcarChipSeleccionado(chip)
        Toast.makeText(this, "✅ Cocina $cocina seleccionada", Toast.LENGTH_SHORT).show()
    }

    private fun marcarChipSeleccionado(chip: TextView) {
        chip.apply {
            setBackgroundColor(Color.parseColor("#FF5722"))
            setTextColor(Color.WHITE)
            alpha = 1f
        }
    }

    private fun mostrarUpgradeCocina(cocina: String, planRequerido: String) {
        val (titulo, precio, beneficios) = when (planRequerido) {
            "PREMIUM" -> Triple(
                "⭐ $cocina — Plan Premium",
                "\$699/año",
                "• 20 escaneos diarios\n• 6 cocinas internacionales\n• Sin anuncios"
            )
            else -> Triple(
                "👑 $cocina — Plan Embajador",
                "\$899/año",
                "• Escaneos ILIMITADOS\n• Todas las cocinas\n• Memoria familiar 💖\n• Fitness, Vegano y Maridaje"
            )
        }
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage("Para cocina $cocina necesitas el plan $planRequerido ($precio):\n\n$beneficios")
            .setPositiveButton("🚀 Quiero este plan") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Usar gratuita", null)
            .show()
    }

    // ─── PLAN ─────────────────────────────────────────────────────────────────

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

    // ─── PERMISOS ─────────────────────────────────────────────────────────────

    private fun solicitarPermisoCamara() {
        when {
            checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> abrirCamara()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("📸 Necesitamos tu cámara")
                    .setMessage(
                        "Chef Vision IA usa la cámara para identificar ingredientes " +
                        "de tu refri y sugerirte recetas deliciosas."
                    )
                    .setPositiveButton("Dar permiso") { _, _ ->
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        txtTip.text = when {
            hora < 6  -> "🌙 Madrugada — todo está tranquilo, ¿planeas el desayuno?"
            hora < 11 -> "🌅 Buenos días — ¿Qué hay en tu refri para el desayuno?"
            hora < 15 -> "☀️ Hora de la comida — escanea y te sugiero algo rico"
            hora < 19 -> "🌆 Tarde — ¿Ya pensaste qué vas a cenar?"
            hora < 22 -> "🌙 Noche — ideal para planear el desayuno de mañana"
            else      -> "🌙 Ya es tarde — descansa, mañana cocinamos algo especial"
        }
    }

    // ─── SEMÁFORO ─────────────────────────────────────────────────────────────

    private fun evaluarYMostrarSemaforo() {
        val inventario = MemoryManager.obtener(this)
        if (inventario.isEmpty()) return

        val prefs = this.getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()

        val rojos     = mutableListOf<Pair<String, String>>()
        val amarillos = mutableListOf<String>()
        val verdes    = mutableListOf<String>()

        inventario.forEach { ingrediente ->
            val key = ingrediente.lowercase().trim()
            val fechaCarga = prefs.getLong(key, 0L)
            if (fechaCarga == 0L) return@forEach

            val diff = ahora - fechaCarga
            val diasPasados = TimeUnit.MILLISECONDS.toDays(diff).toInt()

            val limite = FreshnessManager.freshnessRules[key]
                ?: FreshnessManager.freshnessRules.entries
                    .find { key.contains(it.key) }?.value
                ?: 7

            when {
                diasPasados >= limite -> {
                    val sugerencia = FreshnessManager.sugerencias[key]
                        ?: FreshnessManager.sugerencias.entries
                            .find { (k, _) -> key.contains(k) }?.value
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
            rojos.forEach { (_, s) -> sb.appendLine("   💡 $s") }
            sb.appendLine()
        }
        if (amarillos.isNotEmpty())
            sb.appendLine("🟡 Usar pronto (2-3 días): ${amarillos.joinToString(", ")}\n")
        if (verdes.isNotEmpty())
            sb.appendLine("🟢 Fresco y bien: ${verdes.joinToString(", ")}\n")
        sb.append("🛒 Tip: Martes y jueves — frutas y verduras 20% off en Soriana")

        AlertDialog.Builder(this)
            .setTitle("🚨 Alerta Desperdicio Cero")
            .setMessage(sb.toString())
            .setPositiveButton("👨‍🍳 Cocinar con esto") { _, _ -> mostrarOpciones() }
            .setNeutralButton("🛒 Agregar a lista") { _, _ ->
                CartMemory.agregarLista(this, rojos.map { it.first })
                startActivity(Intent(this, CartActivity::class.java))
            }
            .setNegativeButton("Luego", null)
            .show()
    }

    // ─── CÁMARA ───────────────────────────────────────────────────────────────

    private fun abrirCamara() {
        try {
            val foto = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "chef_scan_${System.currentTimeMillis()}.jpg"
            )
            foto.parentFile?.mkdirs()

            fotoUri = FileProvider.getUriForFile(
                this,
                "com.gustavo.chefvisionia.fileprovider",
                foto
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, fotoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            if (intent.resolveActivity(packageManager) != null) {
                cameraLauncher.launch(intent)
            } else {
                Toast.makeText(this, "⚠️ No se encontró app de cámara", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            fotoUri = null
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }

    // ─── PROCESAR IMAGEN ──────────────────────────────────────────────────────
    private fun procesarImagen(bitmap: Bitmap) {
        txtTip.text = "🔍 Analizando con IA..."
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE
        chipContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val bitmapResized = Bitmap.createScaledBitmap(bitmap, 512, 512, true)

                val contextoFamiliar = buildString {
                    append("Cocina seleccionada: $cocinaSeleccionada. ")
                    append("Sugiere recetas específicas de cocina $cocinaSeleccionada. ")
                    EventMemoryManager.buscarEventoCercano(this@MainActivity)?.let {
                        append("Contexto familiar: cumpleaños de ${it.nombre} el ")
                        append("${it.dia}/${it.mes}. Le gusta: ${it.gustos}.")
                    }
                }

                val ingredientes = GeminiEngine.detectarIngredientes(
                    bitmapResized, contextoFamiliar
                )

                if (ingredientes.isNotEmpty()) {
                    ingredientesDetectados.clear()
                    ingredientesDetectados.addAll(ingredientes)
                    MemoryManager.guardar(this@MainActivity, ingredientes)

                    val editor = getSharedPreferences(
                        "ChefInventory", Context.MODE_PRIVATE
                    ).edit()
                    ingredientes.forEach { ingr ->
                        editor.putLong(ingr.lowercase().trim(), System.currentTimeMillis())
                    }
                    editor.apply()

                    mostrarOpciones()
                    evaluarYMostrarSemaforo()

                } else {
                    // Diagnóstico visible en pantalla
                    val apiVacia = BuildConfig.GEMINI_API_KEY.isEmpty()
                    val keyPreview = if (!apiVacia)
                        BuildConfig.GEMINI_API_KEY.take(8) + "..."
                    else "VACÍA"

                    chipContainer.addView(TextView(this@MainActivity).apply {
                        text = if (apiVacia) {
                            "❌ API Key vacía — la key no llegó al APK.\n\nRecompila desde GitHub Actions."
                        } else {
                            "⚠️ IA no detectó ingredientes.\n\nKey OK: $keyPreview\n\nIntenta:\n• Acercar más la cámara\n• Mejor iluminación\n• Apuntar directo a los alimentos"
                        }
                        textSize = 13f
                        setTextColor(Color.parseColor("#FF5722"))
                        gravity = Gravity.CENTER
                        setPadding(24, 32, 24, 32)
                    })
                }

            } catch (e: Exception) {
                chipContainer.addView(TextView(this@MainActivity).apply {
                    text = "❌ Error: ${e.localizedMessage ?: "desconocido"}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#FF5722"))
                    gravity = Gravity.CENTER
                    setPadding(24, 32, 24, 32)
                })
            } finally {
                btnScan.isEnabled = true
                progressBar.visibility = View.GONE
                mostrarTipPorHora()
            }
        }
    }

    // ─── OPCIONES ─────────────────────────────────────────────────────────────

    private fun mostrarOpciones() {
        chipContainer.removeAllViews()
        chipContainer.addView(TextView(this).apply {
            text = "🥗 Detecté: ${ingredientesDetectados.joinToString(", ")}"
            textSize = 13f
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(16, 8, 16, 16)
            gravity = Gravity.CENTER
        })

        val opciones = RecipeEngine.generarOpciones(ingredientesDetectados)
        if (opciones.isEmpty()) {
            chipContainer.addView(TextView(this).apply {
                text = "😅 No encontré recetas con estos ingredientes.\nIntenta escanear de nuevo."
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(16, 24, 16, 24)
            })
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
                alpha = 0f
                animate().alpha(1f).setDuration(400).start()
            }
            chip.setOnClickListener { manejarSeleccionReceta(receta) }
            chipContainer.addView(chip)
        }
    }

    // ─── RECETA ───────────────────────────────────────────────────────────────

    private fun manejarSeleccionReceta(receta: String) {
        val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)

        if (faltantes.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("🛒 Te falta: ${faltantes.joinToString(", ")}")
                .setMessage(
                    "Para '$receta' te falta: ${faltantes.joinToString(", ")}.\n\n" +
                    "¿Qué deseas hacer?"
                )
                .setPositiveButton("🛒 Agregar al carrito") { _, _ ->
                    CartMemory.agregarLista(this, faltantes)
                    mostrarOpcionesEntrega(faltantes)
                }
                .setNeutralButton("👨‍🍳 Cocinar con lo que tengo") { _, _ ->
                    abrirReceta(receta)
                }
                .setNegativeButton("❌ Cancelar", null)
                .show()
        } else {
            abrirReceta(receta)
        }
    }

    private fun abrirReceta(receta: String) {
        startActivity(Intent(this, RecipeActivity::class.java).apply {
            putExtra("RECETA", receta)
            putExtra("COCINA", cocinaSeleccionada)
            putStringArrayListExtra("INGREDIENTES", ArrayList(ingredientesDetectados))
        })
    }

    // ─── ENTREGA ──────────────────────────────────────────────────────────────

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
            .setNegativeButton("🏪 Yo voy") { _, _ -> mostrarTipTienda() }
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
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
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
                "• 6 cocinas internacionales\n" +
                "• Sin anuncios\n\n" +
                "👑 PLAN EMBAJADOR \$899/año:\n" +
                "• Escaneos ILIMITADOS\n" +
                "• Memoria familiar 💖\n" +
                "• Recordatorios de cumpleaños\n" +
                "• Fitness, Vegano y Maridaje\n" +
                "• Integración Rappi / Uber Eats"
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
