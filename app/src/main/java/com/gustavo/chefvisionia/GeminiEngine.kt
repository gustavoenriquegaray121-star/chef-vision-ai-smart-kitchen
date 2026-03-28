package com.gustavo.chefvisionia

import android.graphics.Bitmap
import android.util.Base64
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

    // ─── CONVERSIÓN ───────────────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // ─── DETECCIÓN DE INGREDIENTES ────────────────────────────────────────────

    suspend fun detectarIngredientes(
        bitmap: Bitmap,
        contextoFamiliar: String = ""   // ← nuevo parámetro con default vacío
    ): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(bitmap)

                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\n\nContexto especial: $contextoFamiliar"
                else ""

                val prompt = """
                    Eres Chef Vision IA, el asistente culinario más inteligente del mundo.
                    Analiza esta imagen y lista SOLO los ingredientes alimenticios que ves.
                    Responde ÚNICAMENTE con una lista separada por comas, en español y en minúsculas.
                    Ejemplo: huevo, tocino, cebolla, tomate
                    No agregues explicaciones, títulos ni texto extra.$contextoExtra
                """.trimIndent()

                val respuesta = llamarGemini(base64Image, prompt)

                // Limpiar y normalizar respuesta
                respuesta
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && it.length > 1 }

            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ─── GENERACIÓN DE RECETA ─────────────────────────────────────────────────

    suspend fun generarReceta(
        nombreReceta: String,
        ingredientesDisponibles: List<String>,
        contextoFamiliar: String = "",
        idioma: String = "español"
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\n\n💖 Contexto especial: $contextoFamiliar. " +
                    "Si es relevante, adapta la receta o agrega un toque especial para la ocasión."
                else ""

                val prompt = """
                    Eres Chef Vision IA, el mejor chef del mundo y amigo de la familia.
                    
                    El usuario quiere preparar: $nombreReceta
                    Ingredientes disponibles: ${ingredientesDisponibles.joinToString(", ")}
                    $contextoExtra
                    
                    Responde en $idioma con este formato exacto:
                    
                    🍽️ RECETA: $nombreReceta
                    
                    ⏱️ Tiempo de preparación: (minutos)
                    
                    📦 Ingredientes necesarios:
                    • (ingrediente 1)
                    • (ingrediente 2)
                    
                    👨‍🍳 Pasos:
                    1. (paso claro y corto)
                    2. (paso claro y corto)
                    3. (paso claro y corto)
                    
                    💡 Tip del Chef:
                    (consejo profesional en 1 línea)
                    
                    🔄 Sustituciones posibles:
                    (si falta algo, qué puede usar en su lugar)
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "❌ Error generando receta: ${e.message}"
            }
        }
    }

    // ─── SUSTITUCIÓN INTELIGENTE ──────────────────────────────────────────────

    suspend fun sugerirSustitucion(
        ingredienteFaltante: String,
        ingredientesDisponibles: List<String>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Eres un chef profesional y práctico.
                    El usuario NO tiene: $ingredienteFaltante
                    Lo que SÍ tiene: ${ingredientesDisponibles.joinToString(", ")}
                    
                    En máximo 2 líneas directas dile:
                    1. Qué puede usar como sustituto de lo que tiene disponible
                    2. Qué tan bien quedará el plato (% de sabor aproximado)
                    
                    Sé amigable, directo y práctico.
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "Sin sustitución disponible por ahora."
            }
        }
    }

    // ─── TIP DE FRESCURA ──────────────────────────────────────────────────────

    suspend fun generarTipFrescura(
        ingrediente: String,
        diasEnRefri: Int
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    El usuario tiene $ingrediente que lleva $diasEnRefri días en el refrigerador.
                    En máximo 2 líneas dile:
                    1. Si debe usarlo hoy o cuántos días más aguanta
                    2. La mejor receta express para no desperdiciarlo
                    Sé directo y amigable. Usa un emoji relevante.
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "Revisa el estado de tu $ingrediente — mejor úsalo pronto."
            }
        }
    }

    // ─── LLAMADA HTTP A GEMINI ────────────────────────────────────────────────

    private fun llamarGemini(base64Image: String?, prompt: String): String {
        val partes = JSONArray()

        // Si hay imagen, va primero
        if (base64Image != null) {
            partes.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                })
            })
        }

        // Siempre va el texto
        partes.put(JSONObject().apply {
            put("text", prompt)
        })

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", partes)
                }
            ))
            // Configuración de seguridad y temperatura
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1024)
            })
        }

        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-1.5-flash:generateContent?key=$apiKey"
        )

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode

        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Sin detalle"
            return "❌ Error $responseCode: $errorBody"
        }

        val responseText = connection.inputStream.bufferedReader().readText()

        return try {
            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        } catch (e: Exception) {
            "❌ Error procesando respuesta de IA."
        }
    }
}
