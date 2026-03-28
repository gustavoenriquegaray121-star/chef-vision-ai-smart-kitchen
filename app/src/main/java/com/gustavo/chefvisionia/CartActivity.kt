package com.gustavo.chefvisionia

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.ListView // Importación explícita para evitar el Unresolved reference
import android.widget.Toast

class CartActivity : AppCompatActivity() {

    private lateinit var lista: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var txtTotal: TextView
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        // Inicialización de vistas
        listView    = findViewById(R.id.listView)
        val btnAdd  = findViewById<Button>(R.id.btnAdd)
        val btnWhats = findViewById<Button>(R.id.btnWhats)
        val btnBack  = findViewById<Button>(R.id.btnBack)
        val btnClear = findViewById<Button>(R.id.btnClear)
        txtTotal     = findViewById(R.id.txtTotal)

        // Carga de datos desde memoria
        lista   = CartMemory.obtenerLista(this)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lista)
        listView.adapter = adapter

        actualizarTotal()

        // Listeners de botones
        btnAdd.setOnClickListener { mostrarDialogoAgregar() }

        btnWhats.setOnClickListener { compartirWhatsApp() }

        btnBack.setOnClickListener { finish() }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Vaciar carrito")
                .setMessage("¿Seguro que quieres eliminar todo?")
                .setPositiveButton("Sí") { _, _ ->
                    lista.clear()
                    CartMemory.limpiar(this)
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                    Toast.makeText(this, "Carrito vacío", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Eliminación individual por pulso largo
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = lista[position]
            AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Eliminar \"$item\"?")
                .setPositiveButton("Eliminar") { _, _ ->
                    lista.removeAt(position)
                    CartMemory.limpiar(this)
                    // Se re-guarda la lista actualizada
                    CartMemory.agregarLista(this, lista)
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                    Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
    }

    private fun mostrarDialogoAgregar() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Agregar producto")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val texto = input.text.toString().trim()
                if (texto.isNotEmpty()) {
                    lista.add(texto)
                    // Sincronización con memoria
                    CartMemory.agregarLista(this, listOf(texto))
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                } else {
                    Toast.makeText(this, "Escribe un producto", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarTotal() {
        // Tu lógica de precio estimado por item
        val precioPorItem = 25
        val total = lista.size * precioPorItem
        txtTotal.text = "Total estimado: $$total MXN"
    }

    private fun compartirWhatsApp() {
        if (lista.isEmpty()) {
            Toast.makeText(this, "La lista está vacía", Toast.LENGTH_SHORT).show()
            return
        }
        val texto = lista.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "🛒 Lista del súper:\n\n$texto")
        }
        startActivity(Intent.createChooser(intent, "Enviar lista"))
    }
}
