package com.gustavo.chefvisionia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private data class Particula(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val color: Int, val size: Float,
        var alpha: Float = 1f,
        var rotacion: Float = 0f,
        val velocidadRot: Float = Random.nextFloat() * 8f - 4f
    )

    private var scanCount = 0
    private var userPlan = "GRATUITO"
    private var cocinaSeleccionada = "Mexicana"
    private val ingredientesDetectados = mutableListOf<String>()
    private var fotoUri: Uri? = null

    private lateinit var chipContainer: LinearLayout
    private lateinit var txtPlan: TextView
    private lateinit var txtTip: TextView
    private lateinit var txtEvento: TextView
    private lateinit var btnScan: Button
    private lateinit var adView: AdView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: View

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
                    return@registerForActivityResult
                } catch (e: Exception) { }
            }
            val thumbnail = result.data?.extras?.get("data") as? Bitmap
            if (thumbnail != null) {
                procesarImagen(thumbnail)
                return@registerForActivityResult
            }
            Toast.makeText(this,
                "⚠️ No se pudo leer la imagen. Intenta de nuevo.",
                Toast.LENGTH_LONG).show()
        }
    }

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

    private fun inicializarVistas() {
        chipContainer = findViewById(R.id.chipContainer)
        txtPlan       = findViewById(R.id.txtPlan)
        txtTip        = findViewById(R.id.txtTip)
        txtEvento     = findViewById(R.id.txtEvento)
        btnScan       = findViewById(R.id.btnScanIngredients)
        adView        = findViewById(R.id.adView)
        progressBar   = findViewById(R.id.progressBar)
        rootLayout    = findViewById(android.R.id.content)
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

    private fun cambiarPlanYActualizar(nuevoPlan: String) {
        val planAnterior = userPlan
        userPlan = nuevoPlan
        scanCount = 0
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().putInt("scan_count", 0).apply()
        if (nuevoPlan == "GRATUITO") cocinaSeleccionada = "Mexicana"
        actualizarUIPlan()
        inicializarChipsCocinas()
        if (nuevoPlan != "GRATUITO" && nuevoPlan != planAnterior) {
            celebrarUpgrade(nuevoPlan)
        } else {
            Toast.makeText(this, "Plan: $nuevoPlan 🚀",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun inicializarListeners() {
        txtPlan.setOnLongClickListener {
            val opciones = arrayOf("🆓 GRATUITO", "⭐ PREMIUM", "👑 EMBAJADOR")
            AlertDialog.Builder(this)
                .setTitle("🛠️ Modo Dev — Cambiar plan")
                .setItems(opciones) { _, which ->
                    val nuevoPlan = when (which) {
                        1 -> "PREMIUM"
                        2 -> "SUPER"
                        else -> "GRATUITO"
                    }
                    cambiarPlanYActualizar(nuevoPlan)
                }
                .show()
            true
        }

        btnScan.setOnClickListener {
            it.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )
            if (puedeEscanear()) solicitarPermisoCamara() else mostrarUpgrade()
        }

        findViewById<View>(R.id.btnGoToCart).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun celebrarUpgrade(plan: String) {
        val esPremium = plan == "PREMIUM"
        val titulo = if (esPremium)
            "⭐ ¡Bienvenido a Premium!"
        else
            "👑 ¡Bienvenido a Embajador!"
        val mensaje = if (esPremium)
            "Ahora tienes 20 escaneos diarios y acceso a 9 cocinas en total.\n\n¡A cocinar!"
        else
            "Has desbloqueado la experiencia completa de Chef Vision IA.\n\nTodas las cocinas, memoria familiar y escaneos ilimitados. 💖"

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("🎉 ¡Vamos!") { _, _ -> }
            .show()

        Handler(Looper.getMainLooper()).postDelayed({
            vibrar(plan)
            Handler(Looper.getMainLooper()).postDelayed({
                lanzarConfeti(plan)
            }, 150)
        }, 400)
    }

    private fun vibrar(plan: String) {
        try {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (plan == "SUPER") {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 80, 60, 80, 60, 120),
                        intArrayOf(0, 200, 0, 200, 0, 255),
                        -1
                    )
                } else {
                    VibrationEffect.createOneShot(120, 180)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                if (plan == "SUPER") {
                    vibrator.vibrate(longArrayOf(0, 80, 60, 80, 60, 120), -1)
                } else {
                    vibrator.vibrate(120)
                }
            }
        } catch (e: Exception) { }
    }

    private fun lanzarConfeti(plan: String) {
        val colores = if (plan == "SUPER") {
            listOf(
                Color.parseColor("#FFD700"), Color.parseColor("#FFA000"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#FF5722"),
                Color.parseColor("#FFFDE7")
            )
        } else {
            listOf(
                Color.parseColor("#FF5722"), Color.parseColor("#FFFFFF"),
                Color.parseColor("#2196F3"), Color.parseColor("#FF7043"),
                Color.parseColor("#BBDEFB")
            )
        }

        val cantidad = if (plan == "SUPER") 80 else 50
        val anchoPantalla = resources.displayMetrics.widthPixels.toFloat()
        val particulas = mutableListOf<Particula>()

        repeat(cantidad) {
            particulas.add(Particula(
                x  = Random.nextFloat() * anchoPantalla,
                y  = -Random.nextFloat() * 200f,
                vx = Random.nextFloat() * 6f - 3f,
                vy = Random.nextFloat() * 4f + 3f,
                color = colores.random(),
                size  = Random.nextFloat() * 12f + 6f
            ))
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var frameCount = 0

        val confettiView = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                frameCount++
                var todasFuera = true
                particulas.forEach { p ->
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.15f
                    p.rotacion += p.velocidadRot
                    if (p.y > height * 0.6f) p.alpha -= 0.018f
                    if (p.alpha < 0f) p.alpha = 0f
                    if (p.y < height + 50f) todasFuera = false
                    paint.color = p.color
                    paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.rotate(p.rotacion, p.x, p.y)
                    canvas.drawRect(
                        p.x - p.size / 2f, p.y - p.size / 4f,
                        p.x + p.size / 2f, p.y + p.size / 4f,
                        paint
                    )
                    canvas.restore()
                }
                if (!todasFuera && frameCount < 180) invalidate()
                else (parent as? FrameLayout)?.removeView(this)
            }
        }

        val decorView = window.decorView as FrameLayout
        confettiView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        decorView.addView(confettiView)

        if (plan == "SUPER") {
            rootLayout.animate()
                .alpha(0.85f).setDuration(200)
                .withEndAction {
                    rootLayout.animate().alpha(1f).setDuration(300).start()
                }.start()
        }
        confettiView.invalidate()
    }

    private fun inicializarChipsCocinas() {
        val chipsMexicana = findViewById<TextView>(R.id.chipMexicana)
        val chipsItaliana = findViewById<TextView>(R.id.chipItaliana)
        val chipsChina    = findViewById<TextView>(R.id.chipChina)

        chipsMexicana.setOnClickListener { seleccionarCocina("Mexicana", chipsMexicana) }
        chipsItaliana.setOnClickListener { seleccionarCocina("Italiana", chipsItaliana) }
        chipsChina.setOnClickListener    { seleccionarCocina("China", chipsChina) }

        listOf(
            R.id.chipFrancesa     to "Francesa",
            R.id.chipJaponesa     to "Japonesa",
            R.id.chipEspanola     to "Española",
            R.id.chipAmericana    to "Americana",
            R.id.chipTailandesa   to "Tailandesa",
            R.id.chipMediterranea to "Mediterránea"
        ).forEach { (id, nombre) ->
            val chip = findViewById<TextView>(id)
            if (userPlan == "PREMIUM" || userPlan == "SUPER") {
                chip.text = nombre
                chip.setBackgroundResource(R.drawable.glass_chip_premium)
                chip.setTextColor(Color.parseColor("#CCCCCC"))
                chip.alpha = 1f
                chip.setOnClickListener { seleccionarCocina(nombre, chip) }
            } else {
                chip.text = "$nombre 🔒"
                chip.setBackgroundResource(R.drawable.glass_chip_premium)
                chip.setTextColor(Color.parseColor("#9999AA"))
                chip.alpha = 1f
                chip.setOnClickListener { mostrarUpgradeCocina(nombre, "PREMIUM") }
            }
        }

        listOf(
            R.id.chipVegana   to "Vegana",
            R.id.chipFitness  to "Fitness",
            R.id.chipMaridaje to "Maridaje"
        ).forEach { (id, nombre) ->
            val chip = findViewById<TextView>(id)
            if (userPlan == "SUPER") {
                chip.text = nombre
                chip.setBackgroundResource(R.drawable.chip_ambassador)
                chip.setTextColor(Color.parseColor("#1A1A00"))
                chip.alpha = 1f
                chip.setOnClickListener { seleccionarCocina(nombre, chip) }
            } else {
                chip.text = "$nombre 🔒"
                chip.setBackgroundResource(R.drawable.chip_ambassador)
                chip.setTextColor(Color.parseColor("#1A1A00"))
                chip.alpha = 1f
                chip.setOnClickListener { mostrarUpgradeCocina(nombre, "EMBAJADOR") }
            }
        }

        val chipActual = when (cocinaSeleccionada) {
            "Mexicana"     -> chipsMexicana
            "Italiana"     -> chipsItaliana
            "China"        -> chipsChina
            "Francesa"     -> findViewById(R.id.chipFrancesa)
            "Japonesa"     -> findViewById(R.id.chipJaponesa)
            "Española"     -> findViewById(R.id.chipEspanola)
            "Americana"    -> findViewById(R.id.chipAmericana)
            "Tailandesa"   -> findViewById(R.id.chipTailandesa)
            "Mediterránea" -> findViewById(R.id.chipMediterranea)
            "Vegana"       -> findViewById(R.id.chipVegana)
            "Fitness"      -> findViewById(R.id.chipFitness)
            "Maridaje"     -> findViewById(R.id.chipMaridaje)
            else           -> chipsMexicana
        }
        marcarChipSeleccionado(chipActual)
    }

    private fun seleccionarCocina(cocina: String, chip: TextView) {
        cocinaSeleccionada = cocina
        listOf(R.id.chipMexicana, R.id.chipItaliana, R.id.chipChina).forEach { id ->
            findViewById<TextView>(id).apply {
                setBackgroundResource(R.drawable.glass_chip_free)
                setTextColor(Color.WHITE)
                alpha = 0.7f
            }
        }
        listOf(
            R.id.chipFrancesa, R.id.chipJaponesa, R.id.chipEspanola,
            R.id.chipAmericana, R.id.chipTailandesa, R.id.chipMediterranea
        ).forEach { id ->
            findViewById<TextView>(id).apply {
                setBackgroundResource(R.drawable.glass_chip_premium)
                setTextColor(Color.parseColor("#CCCCCC"))
                alpha = 1f
            }
        }
        listOf(R.id.chipVegana, R.id.chipFitness, R.id.chipMaridaje).forEach { id ->
            findViewById<TextView>(id).apply {
                setBackgroundResource(R.drawable.chip_ambassador)
                setTextColor(Color.parseColor("#1A1A00"))
                alpha = 1f
            }
        }
        marcarChipSeleccionado(chip)
        Toast.makeText(this, "✅ Cocina $cocina seleccionada",
            Toast.LENGTH_SHORT).show()
    }

    private fun marcarChipSeleccionado(chip: TextView) {
        val idChip = chip.id
        val drawable = when {
            idChip in listOf(
                R.id.chipVegana, R.id.chipFitness, R.id.chipMaridaje
            ) -> R.drawable.chip_ambassador
            idChip in listOf(
                R.id.chipFrancesa, R.id.chipJaponesa, R.id.chipEspanola,
                R.id.chipAmericana, R.id.chipTailandesa, R.id.chipMediterranea
            ) -> R.drawable.chip_premium_selected
            else -> R.drawable.chip_free_selected
        }
        chip.apply {
            setBackgroundResource(drawable)
            setTextColor(Color.WHITE)
            alpha = 1f
        }
    }

    private fun mostrarUpgradeCocina(cocina: String, planRequerido: String) {
        val (titulo, precio, beneficios) = when (planRequerido) {
            "PREMIUM" -> Triple(
                "⭐ $cocina — Plan Premium", "\$699/año",
                "• 20 escaneos diarios\n• +6 cocinas sin candado (9 en total)\n• Sin anuncios"
            )
            else -> Triple(
                "👑 $cocina — Plan Embajador", "\$899/año",
                "• Escaneos ILIMITADOS\n• Todas las cocinas sin candado\n• Memoria familiar 💖\n• Fitness, Vegano y Maridaje"
            )
        }
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage("Para $cocina necesitas el plan $planRequerido ($precio):\n\n$beneficios")
            .setPositiveButton("🚀 Quiero este plan") { _, _ ->
                Toast.makeText(this, "🚀 Próximamente disponible",
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Usar gratuita", null)
            .show()
    }

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

    private fun verificarEventoFamiliar() {
        val evento = EventMemoryManager.buscarEventoCercano(this) ?: return
        val mensaje = EventMemoryManager.obtenerMensajeEvento(evento)
        if (mensaje.isNotEmpty()) {
            txtEvento.visibility = View.VISIBLE
            txtEvento.text = mensaje
            btnScan.text = "🎂 SCAN"
        }
    }

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

    private fun evaluarYMostrarSemaforo() {
        val inventario = MemoryManager.obtener(this)
        if (inventario.isEmpty()) return

        val prefs = getSharedPreferences("ChefInventory", Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        val rojos     = mutableListOf<Pair<String, String>>()
        val amarillos = mutableListOf<String>()
        val verdes    = mutableListOf<String>()

        inventario.forEach { ingrediente ->
            val key = ingrediente.lowercase().trim()
            val fechaCarga = prefs.getLong(key, 0L)
            if (fechaCarga == 0L) return@forEach
            val diasPasados = TimeUnit.MILLISECONDS
                .toDays(ahora - fechaCarga).toInt()
            val limite = FreshnessManager.freshnessRules[key]
                ?: FreshnessManager.freshnessRules.entries
                    .find { key.contains(it.key) }?.value ?: 7
            when {
                diasPasados >= limite -> {
                    val sug = FreshnessManager.sugerencias[key]
                        ?: FreshnessManager.sugerencias.entries
                            .find { (k, _) -> key.contains(k) }?.value
                        ?: "¡Cocina algo rico antes de que se pierda!"
                    rojos.add(Pair(ingrediente, sug))
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

    private fun abrirCamara() {
        try {
            val foto = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "chef_scan_${System.currentTimeMillis()}.jpg"
            )
            foto.parentFile?.mkdirs()
            fotoUri = FileProvider.getUriForFile(
                this, "com.gustavo.chefvisionia.fileprovider", foto
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, fotoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            fotoUri = null
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }

    // ─── PROCESAR IMAGEN — usa GeminiEngine por HTTP, sin SDK ────────────────
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
                        "ChefInventory", Context.MODE_PRIVATE).edit()
                    ingredientes.forEach { ingr ->
                        editor.putLong(ingr.lowercase().trim(), System.currentTimeMillis())
                    }
                    editor.apply()
                    mostrarOpciones()
                    evaluarYMostrarSemaforo()
                } else {
                    val apiVacia = BuildConfig.GEMINI_API_KEY.isEmpty()
                    chipContainer.addView(TextView(this@MainActivity).apply {
                        text = if (apiVacia)
                            "❌ API Key vacía — recompila desde GitHub Actions."
                        else
                            "⚠️ IA no detectó ingredientes.\n\nIntenta:\n• Acercar más la cámara\n• Mejor iluminación\n• Apuntar directo a los alimentos"
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
                text = "😅 No encontré recetas.\nIntenta escanear de nuevo."
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

    private fun manejarSeleccionReceta(receta: String) {
        val faltantes = SmartCartManager.detectarFaltantes(receta, ingredientesDetectados)
        if (faltantes.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("🛒 Te falta: ${faltantes.joinToString(", ")}")
                .setMessage("Para '$receta' te falta: ${faltantes.joinToString(", ")}.\n\n¿Qué deseas hacer?")
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

    private fun mostrarOpcionesEntrega(faltantes: List<String>) {
        val query = faltantes.joinToString("+")
        AlertDialog.Builder(this)
            .setTitle("🚚 ¿Cómo consigues lo que falta?")
            .setMessage("Falta: ${faltantes.joinToString(", ")}")
            .setPositiveButton("🛵 Rappi") { _, _ ->
                abrirApp("com.grability.rappi",
                    "https://www.rappi.com.mx/super/search?query=$query")
            }
            .setNeutralButton("🚗 Uber Eats") { _, _ ->
                abrirApp("com.ubercab.eats",
                    "https://www.ubereats.com/mx/search?q=$query")
            }
            .setNegativeButton("📦 DiDi") { _, _ ->
                abrirApp("com.didiglobal.imhere",
                    "https://food.didiglobal.com/mx")
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

    private fun mostrarUpgrade() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Desbloquea Chef Vision")
            .setMessage(
                "Has agotado tus escaneos gratuitos de hoy.\n\n" +
                "⭐ PREMIUM \$699/año:\n" +
                "• 20 escaneos diarios\n" +
                "• +6 cocinas sin candado (9 en total)\n" +
                "• Sin anuncios\n\n" +
                "👑 PLAN EMBAJADOR \$899/año:\n" +
                "• Escaneos ILIMITADOS\n" +
                "• Todas las cocinas sin candado\n" +
                "• Memoria familiar 💖\n" +
                "• Recordatorios de cumpleaños\n" +
                "• Fitness, Vegano y Maridaje"
            )
            .setPositiveButton("👑 Plan Embajador") { _, _ ->
                cambiarPlanYActualizar("SUPER")
            }
            .setNeutralButton("⭐ Premium") { _, _ ->
                cambiarPlanYActualizar("PREMIUM")
            }
            .setNegativeButton("Luego", null)
            .show()
    }
}
