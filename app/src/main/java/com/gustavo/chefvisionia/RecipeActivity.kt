package com.gustavo.chefvisionia

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class RecipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe)

        val nombre = intent.getStringExtra("RECETA") ?: ""

        val img = findViewById<ImageView>(R.id.imgFood)
        val txt = findViewById<TextView>(R.id.txtReceta)
        val btnBack = findViewById<Button>(R.id.btnBack)

        cargarImagen(nombre, img)
        txt.text = obtenerReceta(nombre)

        btnBack.setOnClickListener {
            finish() // SIEMPRE regresa a pantalla 1
        }
    }

    private fun cargarImagen(nombre: String, imageView: ImageView) {

        val url = when(nombre) {

            "Omelette con tocino" ->
                "https://images.unsplash.com/photo-1604908176997-4316d0e3f1d5"

            "Huevos a la mexicana" ->
                "https://images.unsplash.com/photo-1604908812739-0b7b1e8b4a4f"

            "Papas con tocino" ->
                "https://images.unsplash.com/photo-1604908554020-d5f5b8bdbd2b"

            else ->
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c"
        }

        Glide.with(this)
            .load(url)
            .into(imageView)
    }

    private fun obtenerReceta(nombre: String): String {

        return when(nombre) {

            "Omelette con tocino" ->
                """
                🥓 Omelette con tocino

                Ingredientes:
                - Huevo
                - Tocino
                - Cebolla

                Preparación:
                1. Bate los huevos
                2. Fríe el tocino
                3. Sofríe la cebolla
                4. Mezcla todo y cocina
                """.trimIndent()

            "Huevos a la mexicana" ->
                """
                🇲🇽 Huevos a la mexicana

                Ingredientes:
                - Huevo
                - Tomate
                - Cebolla
                - Chile

                Preparación:
                1. Sofríe tomate, cebolla y chile
                2. Agrega los huevos
                3. Mezcla y cocina
                """.trimIndent()

            "Papas con tocino" ->
                """
                🥔 Papas con tocino

                Ingredientes:
                - Papa
                - Tocino
                - Sal

                Preparación:
                1. Fríe el tocino
                2. Agrega las papas
                3. Cocina hasta dorar
                """.trimIndent()

            else ->
                "Receta no disponible"
        }
    }
}
