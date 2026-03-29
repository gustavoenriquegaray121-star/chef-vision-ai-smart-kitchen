package com.gustavo.chefvisionia

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class RecipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe)

        val nombre      = intent.getStringExtra("RECETA") ?: ""
        val cocina      = intent.getStringExtra("COCINA") ?: "Mexicana"
        val ingredientes = intent.getStringArrayListExtra("INGREDIENTES") ?: arrayListOf()

        val imgFood   = findViewById<ImageView>(R.id.imgFood)
        val txtTitulo = findViewById<TextView>(R.id.txtTitulo)
        val txtReceta = findViewById<TextView>(R.id.txtReceta)
        val btnBack   = findViewById<ImageButton>(R.id.btnBack)

        txtTitulo.text = nombre

        // Cargar imagen
        cargarImagen(nombre, imgFood)

        // Mostrar receta base mientras Gemini responde
        txtReceta.text = "🔍 Generando receta con IA..."

        // Generar receta con Gemini
        lifecycleScope.launch {
            try {
                val contextoFamiliar = EventMemoryManager
                    .buscarEventoCercano(this@RecipeActivity)
                    ?.let { "Cocina especial para ${it.nombre} que le encanta: ${it.gustos}" }
                    ?: ""

                val recetaIA = GeminiEngine.generarReceta(
                    nombreReceta = nombre,
                    ingredientesDisponibles = ingredientes.toList(),
                    contextoFamiliar = contextoFamiliar,
                    idioma = "español"
                )

                txtReceta.text = if (recetaIA.startsWith("❌")) {
                    obtenerRecetaOffline(nombre)
                } else {
                    recetaIA
                }

            } catch (e: Exception) {
                txtReceta.text = obtenerRecetaOffline(nombre)
            }
        }

        btnBack.setOnClickListener { finish() }
    }

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

            else ->
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=800"
        }

        Glide.with(this)
            .load(url)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imageView)
    }

    // Recetas offline de respaldo
    private fun obtenerRecetaOffline(nombre: String): String {
        return when {
            nombre.contains("omelette", ignoreCase = true) -> """
                🥓 Omelette con tocino
                ⏱️ 10 minutos

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

                💡 Tip: No muevas demasiado para que quede esponjoso
            """.trimIndent()

            nombre.contains("mexicana", ignoreCase = true) -> """
                🇲🇽 Huevos a la mexicana
                ⏱️ 10 minutos

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

                💡 Tip: El chile puedes quitarle las semillas si no quieres picante
            """.trimIndent()

            nombre.contains("papa", ignoreCase = true) -> """
                🥔 Papas con tocino
                ⏱️ 20 minutos

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

                💡 Tip: Precuece las papas en microondas 3 min para hacerlo más rápido
            """.trimIndent()

            else -> """
                🍽️ $nombre
                
                Receta generada por Chef Vision IA.
                Escanea tus ingredientes para obtener una receta personalizada.
            """.trimIndent()
        }
    }
}
