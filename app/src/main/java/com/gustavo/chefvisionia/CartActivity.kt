package com.gustavo.chefvisionia

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class CartActivity : AppCompatActivity() {

    private val lista = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        val listView = findViewById<ListView>(R.id.listView)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnWhats = findViewById<Button>(R.id.btnWhats)
        val btnBack = findViewById<Button>(R.id.btnBack)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lista)
        listView.adapter = adapter

        btnAdd.setOnClickListener {
            mostrarDialogoAgregar()
        }

        btnWhats.setOnClickListener {
            compartirWhatsApp()
        }

        btnBack.setOnClickListener {
            finish() // SIEMPRE regresa a pantalla 1
        }
    }

    private fun mostrarDialogoAgregar() {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Agregar producto")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val texto = input.text.toString()
                if (texto.isNotEmpty()) {
                    lista.add(texto)
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun compartirWhatsApp() {
        val texto = lista.joinToString("\n")

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, "🛒 Lista del súper:\n\n$texto")

        startActivity(Intent.createChooser(intent, "Enviar lista"))
    }
}
