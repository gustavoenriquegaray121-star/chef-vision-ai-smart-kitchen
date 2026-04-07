package com.gustavo.chefvisionia

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiEngine {

    var apiKey: String = ""
    private const val TAG = "GEMINI_DEBUG"

    private val modelos = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite"
    )

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val bytes = outputStream.toByteArray()
        Log.d(TAG, "Bitmap bytes: ${bytes.size}")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun detectarIngredientes(
        bitmap: Bitmap,
        contextoFamiliar: String = ""
    ): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== INICIO DETECCIÓN ===")
                Log.d(TAG, "API Key longitud: ${apiKey.length}")

                val bitmapFinal = if (bitmap.width > 384 || bitmap.height > 384) {
                    Bitmap.createScaledBitmap(bitmap, 384, 384, true)
                } else bitmap

                val base64Image = bitmapToBase64(bitmapFinal)
                Log.d(TAG, "base64 length: ${base64Image.length}")

                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\n\nContexto: $contextoFamiliar"
                else ""

                val prompt = """
                    Eres Chef Vision IA. Analiza esta imagen.
                    
                    Identifica TODO lo comestible:
                    - Ingredientes sueltos, frutas, verduras, carnes, lácteos
                    - Empaques de supermercado
                    - Comida preparada, platillos, bebidas
                    
                    Ejemplos:
                    - Sabritas = papas fritas
                    - Oreo = galletas
                    - Del Valle = jugo
                    - Bubulubu = chocolate
                    - Tacos = tortilla, carne, cebolla, cilantro
                    
                    Responde SOLO con ingredientes separados por comas en español.
                    Sin explicaciones. Solo la lista.
                    Ejemplo: papas fritas, galletas, jugo
                    
                    Si no hay nada comestible responde: NINGUNO$contextoExtra
                """.trimIndent()

                // ─── Tokens bajos para detección — solo necesita una lista ──
                val respuesta = llamarGemini(base64Image, prompt, modelos[0], maxTokens = 128)
                    .takeIf { !it.startsWith("❌") }
                    ?: llamarGemini(base64Image, prompt, modelos[1], maxTokens = 128)
                        .takeIf { !it.startsWith("❌") }
                    ?: llamarGemini(base64Image, prompt, modelos[2], maxTokens = 128)
                    ?: "NINGUNO"

                Log.d(TAG, "Respuesta detección: $respuesta")

                if (respuesta.contains("NINGUNO", ignoreCase = true) ||
                    respuesta.startsWith("❌")) {
                    return@withContext emptyList()
                }

                val ingredientes = respuesta
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && it.length > 1 }

                Log.d(TAG, "Ingredientes: $ingredientes")
                ingredientes

            } catch (e: Exception) {
                Log.e(TAG, "EXCEPCIÓN detección: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun generarReceta(
        nombreReceta: String,
        ingredientesDisponibles: List<String>,
        contextoFamiliar: String = "",
        idioma: String = "español"
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\nContexto especial: $contextoFamiliar"
                else ""

                // ─── Prompt directo, sin saludos, formato estricto ────────
                val prompt = """
                    Genera una receta para: $nombreReceta
                    Ingredientes disponibles: ${ingredientesDisponibles.joinToString(", ")}
                    $contextoExtra
                    
                    IMPORTANTE: Responde DIRECTAMENTE con el formato siguiente.
                    NO saludes. NO te presentes. NO agregues introducción.
                    Empieza con el emoji de plato:
                    
                    🍽️ RECETA: $nombreReceta
                    ⏱️ Tiempo: X minutos
                    
                    📦 Ingredientes:
                    • ingrediente 1
                    • ingrediente 2
                    • ingrediente 3
                    
                    👨‍🍳 Pasos:
                    1. Paso uno concreto.
                    2. Paso dos concreto.
                    3. Paso tres concreto.
                    
                    💡 Tip: consejo breve en una línea.
                """.trimIndent()

                // ─── Tokens altos para receta completa ────────────────────
                val resultado = llamarGemini(null, prompt, modelos[0], maxTokens = 800)
                    .takeIf { !it.startsWith("❌") }
                    ?: llamarGemini(null, prompt, modelos[1], maxTokens = 800)
                        .takeIf { !it.startsWith("❌") }
                    ?: llamarGemini(null, prompt, modelos[2], maxTokens = 800)
                    ?: "❌ No se pudo generar la receta"

                resultado

            } catch (e: Exception) {
                "❌ Error: ${e.message}"
            }
        }
    }

    suspend fun sugerirSustitucion(
        ingredienteFaltante: String,
        ingredientesDisponibles: List<String>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Sin: $ingredienteFaltante
                    Con: ${ingredientesDisponibles.joinToString(", ")}
                    En 2 líneas: sustituto posible y % de sabor resultante.
                """.trimIndent()
                llamarGemini(null, prompt, modelos[0], maxTokens = 100)
                    .takeIf { !it.startsWith("❌") }
                    ?: "Puedes continuar con lo que tienes — quedará delicioso."
            } catch (e: Exception) {
                "Puedes continuar con lo que tienes — quedará delicioso."
            }
        }
    }

    suspend fun generarTipFrescura(
        ingrediente: String,
        diasEnRefri: Int
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    $ingrediente lleva $diasEnRefri días en el refri.
                    2 líneas: ¿cuántos días más aguanta? y receta express.
                """.trimIndent()
                llamarGemini(null, prompt, modelos[0], maxTokens = 100)
                    .takeIf { !it.startsWith("❌") }
                    ?: "Revisa tu $ingrediente — mejor úsalo pronto. 🕐"
            } catch (e: Exception) {
                "Revisa tu $ingrediente — mejor úsalo pronto. 🕐"
            }
        }
    }

    private fun llamarGeminiConFallback(
        base64Image: String?,
        prompt: String
    ): String {
        var ultimoError = ""
        for (modelo in modelos) {
            val resultado = llamarGemini(base64Image, prompt, modelo, maxTokens = 512)
            if (!resultado.startsWith("❌")) return resultado
            ultimoError = resultado
        }
        return ultimoError
    }

    private fun llamarGemini(
        base64Image: String?,
        prompt: String,
        modelo: String,
        maxTokens: Int = 512
    ): String {
        return try {
            val partsArray = JSONArray()
            partsArray.put(JSONObject().apply { put("text", prompt) })

            if (base64Image != null) {
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    })
                })
            }

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply { put("parts", partsArray) }
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", maxTokens)
                })
            }

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$modelo:generateContent?key=$apiKey"
            )

            Log.d(TAG, "POST $modelo maxTokens=$maxTokens")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            OutputStreamWriter(connection.outputStream, "UTF-8").use {
                it.write(requestBody.toString())
                it.flush()
            }

            val code = connection.responseCode
            Log.d(TAG, "Response $code [$modelo]")

            if (code != 200) {
                val err = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: "sin detalle"
                Log.e(TAG, "Error $code: $err")
                return "❌ Error $code: $err"
            }

            val responseText = connection.inputStream
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            val json = JSONObject(responseText)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return "❌ Sin candidatos"

            val text = candidates
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Log.d(TAG, "OK [$modelo]: ${text.take(100)}")
            text

        } catch (e: Exception) {
            Log.e(TAG, "Excepción [$modelo]: ${e.message}", e)
            "❌ Excepción: ${e.message}"
        }
    }
}
