package com.gustavo.chefvisionia

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.util.Locale

class RecipeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var txtReceta: TextView
    private lateinit var txtTitulo: TextView
    private lateinit var txtInfo: TextView
    private lateinit var btnLeer: Button
    private lateinit var tts: TextToSpeech

    private var ttsListo = false
    private var leyendo = false
    private var textoReceta = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe)

        val nombre      = intent.getStringExtra("RECETA") ?: ""
        val cocina      = intent.getStringExtra("COCINA") ?: "Mexicana"
        val ingredientes = intent.getStringArrayListExtra("INGREDIENTES")
            ?: arrayListOf()

        txtTitulo = findViewById(R.id.txtTitulo)
        txtReceta = findViewById(R.id.txtReceta)
        txtInfo   = findViewById(R.id.txtInfo)
        btnLeer   = findViewById(R.id.btnLeer)
        val imgFood = findViewById<ImageView>(R.id.imgFood)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // Inicializar TTS
        tts = TextToSpeech(this, this)

        txtTitulo.text = nombre
        txtReceta.text = "🔍 Generando receta con IA..."

        cargarImagen(nombre, imgFood)

        // Generar receta con Gemini
        lifecycleScope.launch {
            try {
                val contextoFamiliar = EventMemoryManager
                    .buscarEventoCercano(this@RecipeActivity)
                    ?.let {
                        "Cocina especial para ${it.nombre} " +
                        "que le encanta: ${it.gustos}"
                    } ?: ""

                val recetaIA = GeminiEngine.generarReceta(
                    nombreReceta = nombre,
                    ingredientesDisponibles = ingredientes.toList(),
                    contextoFamiliar = contextoFamiliar,
                    idioma = "español"
                )

                textoReceta = if (recetaIA.startsWith("❌")) {
                    obtenerRecetaOffline(nombre)
                } else {
                    recetaIA
                }

                txtReceta.text = textoReceta
                txtInfo.text = extraerTiempo(textoReceta)
                btnLeer.isEnabled = true

            } catch (e: Exception) {
                textoReceta = obtenerRecetaOffline(nombre)
                txtReceta.text = textoReceta
                btnLeer.isEnabled = true
            }
        }

        // ─── Botón TTS ────────────────────────────────────────────────────────
        btnLeer.isEnabled = false
        btnLeer.setOnClickListener {
            if (!ttsListo) return@setOnClickListener
            if (leyendo) {
                tts.stop()
                leyendo = false
                btnLeer.text = "🔊 Leer"
            } else {
                leerReceta()
            }
        }

        btnBack.setOnClickListener {
            tts.stop()
            finish()
        }

        // ─── Botón carrito ────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnCarrito).setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    // ─── TTS INIT ─────────────────────────────────────────────────────────────
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es", "MX"))
            ttsListo = result != TextToSpeech.LANG_MISSING_DATA &&
                       result != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsListo && textoReceta.isNotEmpty()) {
                btnLeer.isEnabled = true
            }
        }
    }

    private fun leerReceta() {
        if (textoReceta.isEmpty()) return
        leyendo = true
        btnLeer.text = "⏹ Detener"

        // Limpiar emojis y markdown para mejor pronunciación
        val textoLimpio = textoReceta
            .replace(Regex("[🍽️⏱️📦👨‍🍳💡🔄🥗🍅🥑🌿🍗🥩🐟🥛🍳🧅•]"), "")
            .replace("**", "")
            .replace("#", "")
            .trim()

        tts.speak(textoLimpio, TextToSpeech.QUEUE_FLUSH, null, "receta")

        // Detectar cuando termina de hablar
        tts.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        leyendo = false
                        btnLeer.text = "🔊 Leer"
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        leyendo = false
                        btnLeer.text = "🔊 Leer"
                    }
                }
            }
        )
    }

    // ─── EXTRAER TIEMPO ───────────────────────────────────────────────────────
    private fun extraerTiempo(receta: String): String {
        val regex = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE)
        val match = regex.find(receta)
        return if (match != null) {
            "⏱️ ${match.groupValues[1]} min  •  🍽️ Chef Vision IA"
        } else {
            "⏱️ Chef Vision IA  •  🍽️ Receta personalizada"
        }
    }

    // ─── IMAGEN ───────────────────────────────────────────────────────────────
    private fun cargarImagen(nombre: String, imageView: ImageView) {
        val url = when {
            nombre.contains("omelette", ignoreCase = true) ||
            nombre.contains("tocino", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1565299585323-38d6b0865b47?w=800"
            nombre.contains("mexicana", ignoreCase = true) ||
            nombre.contains("huevo", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=800"
            nombre.contains("papa", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1518013431117-eb1465fa5752?w=800"
            nombre.contains("chorizo", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1624374642043-8f8e08af2a40?w=800"
            nombre.contains("ensalada", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800"
            nombre.contains("pasta", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1473093226795-af9932fe5856?w=800"
            nombre.contains("tacos", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1565299585323-38d6b0865b47?w=800"
            nombre.contains("sopa", ignoreCase = true) ||
            nombre.contains("caldo", ignoreCase = true) ->
                "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=800"
            else ->
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=800"
        }

        Glide.with(this)
            .load(url)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imageView)
    }

    // ─── RECETAS OFFLINE ──────────────────────────────────────────────────────
    private fun obtenerRecetaOffline(nombre: String): String {
        return when {
            nombre.contains("omelette", ignoreCase = true) -> """
                🍽️ RECETA: Omelette con tocino
                ⏱️ Tiempo: 10 minutos

                📦 Ingredientes:
                • 3 huevos
                • 100g tocino
                • ½ cebolla
                • Sal y pimienta

                👨‍🍳 Pasos:
                1. Bate los huevos con sal y pimienta
                2. Fríe el tocino hasta dorar
                3. Sofríe la cebolla en la misma sartén
                4. Vierte los huevos y cocina a fuego medio
                5. Dobla el omelette y sirve caliente

                💡 Tip del Chef: No muevas demasiado para que quede esponjoso
            """.trimIndent()

            nombre.contains("mexicana", ignoreCase = true) -> """
                🍽️ RECETA: Huevos a la mexicana
                ⏱️ Tiempo: 10 minutos

                📦 Ingredientes:
                • 3 huevos
                • 1 tomate
                • ½ cebolla
                • 1 chile serrano
                • Sal al gusto

                👨‍🍳 Pasos:
                1. Pica tomate, cebolla y chile finamente
                2. Sofríe todo en aceite por 3 minutos
                3. Agrega los huevos batidos
                4. Mezcla constantemente hasta cuajar
                5. Sirve con tortillas calientes

                💡 Tip del Chef: Quita las semillas al chile si no quieres picante
            """.trimIndent()

            nombre.contains("papa", ignoreCase = true) -> """
                🍽️ RECETA: Papas con tocino
                ⏱️ Tiempo: 20 minutos

                📦 Ingredientes:
                • 3 papas medianas
                • 150g tocino
                • Sal y pimienta

                👨‍🍳 Pasos:
                1. Corta las papas en cubos
                2. Fríe el tocino hasta dorar
                3. Agrega las papas en la misma sartén
                4. Cocina 15 minutos moviendo ocasionalmente
                5. Sazona con sal y pimienta

                💡 Tip del Chef: Precuece las papas en microondas 3 min para ir más rápido
            """.trimIndent()

            else -> """
                🍽️ RECETA: $nombre

                Escanea tus ingredientes para obtener
                una receta personalizada por IA.

                💡 Tip: Apunta la cámara directo a los alimentos
                con buena iluminación.
            """.trimIndent()
        }
    }

    // ─── CICLO DE VIDA ────────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) tts.stop()
        leyendo = false
        if (::btnLeer.isInitialized) btnLeer.text = "🔊 Leer"
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
