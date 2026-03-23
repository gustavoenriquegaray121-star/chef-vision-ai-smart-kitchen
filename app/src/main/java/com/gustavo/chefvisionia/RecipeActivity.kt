package com.gustavo.chefvisionia

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RecipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe)

        val receta = intent.getStringExtra("RECETA") ?: ""

        val txtTitulo = findViewById<TextView>(R.id.txtTitulo)
        val txtContenido = findViewById<TextView>(R.id.txtContenido)
        val btnBack = findViewById<Button>(R.id.btnBack)

        txtTitulo.text = receta
        txtContenido.text = obtenerReceta(receta)

        btnBack.setOnClickListener {
            finish() // regresa a pantalla 1
        }
    }

    private fun obtenerReceta(nombre: String): String {
        return when(nombre) {

            "Omelette con tocino" ->
                "1. Bate los huevos\n2. Fríe el tocino\n3. Mezcla\n4. Cocina en sartén"

            "Huevos a la mexicana" ->
                "1. Pica tomate, cebolla y chile\n2. Sofríe\n3. Agrega huevo\n4. Cocina"

            "Huevo con chorizo" ->
                "1. Fríe chorizo\n2. Agrega huevo\n3. Mezcla y cocina"

            "Papas con huevo" ->
                "1. Fríe papas\n2. Agrega huevo\n3. Mezcla y cocina"

            else ->
                "Receta básica:\n1. Prepara ingredientes\n2. Cocina al gusto"
        }
    }
}
