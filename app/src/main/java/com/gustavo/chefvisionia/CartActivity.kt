package com.gustavo.chefvisionia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CartActivity : AppCompatActivity() {

    private lateinit var lista: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var txtTotal: TextView
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        listView         = findViewById(R.id.listView)
        val btnAdd       = findViewById<Button>(R.id.btnAdd)
        val btnWhats     = findViewById<Button>(R.id.btnWhats)
        val btnBack      = findViewById<ImageButton>(R.id.btnBack)
        val btnClear     = findViewById<Button>(R.id.btnClear)
        val btnRappi     = findViewById<Button>(R.id.btnRappi)
        val btnUber      = findViewById<Button>(R.id.btnUber)
        val btnDidi      = findViewById<Button>(R.id.btnDidi)
        txtTotal         = findViewById(R.id.txtTotal)

        lista   = CartMemory.obtenerLista(this)
        adapter = ArrayAdapter(this, R.layout.item_cart, R.id.txtItem, lista)
        listView.adapter = adapter

        actualizarTotal()

        btnAdd.setOnClickListener   { mostrarDialogoAgregar() }
        btnWhats.setOnClickListener { compartirWhatsApp() }
        btnBack.setOnClickListener  { finish() }

        btnRappi.setOnClickListener {
            abrirDelivery(
                "com.grability.rappi",
                "https://www.rappi.com.mx"
            )
        }
        btnUber.setOnClickListener {
            abrirDelivery(
                "com.ubercab.eats",
                "https://www.ubereats.com/mx"
            )
        }
        btnDidi.setOnClickListener {
            abrirDelivery(
                "com.didiglobal.imhere",
                "https://www.didifood.com/mx"
            )
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🗑️ Vaciar lista")
                .setMessage("¿Seguro que quieres eliminar todo?")
                .setPositiveButton("Sí") { _, _ ->
                    lista.clear()
                    CartMemory.limpiar(this)
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                    Toast.makeText(this,
                        "Lista vacía ✅",
                        Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = lista[position]
            AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Eliminar \"$item\"?")
                .setPositiveButton("Eliminar") { _, _ ->
                    lista.removeAt(position)
                    CartMemory.limpiar(this)
                    CartMemory.agregarLista(this, lista)
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                    Toast.makeText(this,
                        "Eliminado ✅",
                        Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
    }

    // ─── AGREGAR PRODUCTO ─────────────────────────────────────────────────────
    private fun mostrarDialogoAgregar() {
        val input = EditText(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            hint = "Ej: tomate, leche, huevos..."
        }
        AlertDialog.Builder(this)
            .setTitle("➕ Agregar producto")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val texto = input.text.toString().trim()
                if (texto.isNotEmpty()) {
                    lista.add(texto)
                    CartMemory.agregarLista(this, listOf(texto))
                    adapter.notifyDataSetChanged()
                    actualizarTotal()
                } else {
                    Toast.makeText(this,
                        "Escribe un producto",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── TOTAL ────────────────────────────────────────────────────────────────
    private fun actualizarTotal() {
        if (lista.isEmpty()) {
            txtTotal.text = "Lista vacía — agrega productos 🛒"
            return
        }
        val cantidad = lista.size
        val texto = if (cantidad == 1)
            "🛒 1 producto en tu lista"
        else
            "🛒 $cantidad productos en tu lista"
        txtTotal.text = texto
    }

    // ─── WHATSAPP ─────────────────────────────────────────────────────────────
    private fun compartirWhatsApp() {
        if (lista.isEmpty()) {
            Toast.makeText(this,
                "La lista está vacía",
                Toast.LENGTH_SHORT).show()
            return
        }
        val numerados = lista
            .mapIndexed { i, item -> "${i + 1}. $item" }
            .joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "🛒 Mi Lista del Súper — Chef Vision IA\n\n" +
                "$numerados\n\n" +
                "💎 v.25 Certified by Altea-Garay"
            )
        }
        startActivity(Intent.createChooser(intent, "Enviar lista"))
    }

    // ─── DELIVERY ─────────────────────────────────────────────────────────────
    private fun abrirDelivery(paquete: String, urlFallback: String) {
        if (lista.isEmpty()) {
            Toast.makeText(this,
                "Agrega productos primero 🛒",
                Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = packageManager
                .getLaunchIntentForPackage(paquete)
            startActivity(
                intent ?: Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(urlFallback)
                )
            )
        } catch (e: Exception) {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse(urlFallback))
            )
        }
    }
}
