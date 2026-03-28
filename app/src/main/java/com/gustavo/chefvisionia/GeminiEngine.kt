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

    // ─── MODELOS DISPONIBLES (fallback automático) ────────────────────────────
    private val modelos = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-pro-latest"
    )

    // ─── CONVERSIÓN ───────────────────────────────────────────────────────────

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // ─── DETECCIÓN DE INGREDIENTES ────────────────────────────────────────────

    suspend fun detectarIngredientes(
        bitmap: Bitmap,
        contextoFamiliar: String = ""
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
                    Si no ves ingredientes alimenticios en la imagen, responde exactamente: NINGUNO
                    Responde ÚNICAMENTE con una lista separada por comas, en español y en minúsculas.
                    Ejemplo: huevo, tocino, cebolla, tomate
                    No agregues explicaciones, títulos ni texto extra.$contextoExtra
                """.trimIndent()

                val respuesta = llamarGeminiConFallback(base64Image, prompt)

                if (respuesta.startsWith("❌") ||
                    respuesta.contains("NINGUNO", ignoreCase = true)) {
                    return@withContext emptyList()
                }

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
                    "Si es relevante, adapta la receta o agrega un toque especial."
                else ""

                val prompt = """
                    Eres Chef Vision IA, el mejor chef del mundo y amigo de la familia.
                    
                    El usuario quiere preparar: $nombreReceta
                    Ingredientes disponibles: ${ingredientesDisponibles.joinToString(", ")}
                    $contextoExtra
                    
                    Responde en $idioma con este formato exacto:
                    
                    🍽️ RECETA: $nombreReceta
                    ⏱️ Tiempo: (minutos)
                    
                    📦 Ingredientes:
                    • (ingrediente 1)
                    • (ingrediente 2)
                    
                    👨‍🍳 Pasos:
                    1. (paso claro)
                    2. (paso claro)
                    3. (paso claro)
                    
                    💡 Tip del Chef: (1 línea)
                    🔄 Sustituciones: (si falta algo)
                """.trimIndent()

                llamarGeminiConFallback(null, prompt)

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
                    
                    En máximo 2 líneas dile:
                    1. Qué puede usar como sustituto
                    2. Qué tan bien quedará el plato (% de sabor)
                    
                    Sé amigable y directo.
                """.trimIndent()

                llamarGeminiConFallback(null, prompt)

            } catch (e: Exception) {
                "Puedes continuar con los ingredientes que tienes — quedará delicioso."
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

                llamarGeminiConFallback(null, prompt)

            } catch (e: Exception) {
                "Revisa el estado de tu $ingrediente — mejor úsalo pronto. 🕐"
            }
        }
    }

    // ─── LLAMADA CON FALLBACK AUTOMÁTICO ─────────────────────────────────────

    private fun llamarGeminiConFallback(base64Image: String?, prompt: String): String {
        var ultimoError = ""
        for (modelo in modelos) {
            val resultado = llamarGemini(base64Image, prompt, modelo)
            if (!resultado.startsWith("❌")) {
                return resultado
            }
            ultimoError = resultado
        }
        return ultimoError
    }

    // ─── LLAMADA HTTP A GEMINI ────────────────────────────────────────────────

    private fun llamarGemini(
        base64Image: String?,
        prompt: String,
        modelo: String = modelos.first()
    ): String {
        return try {
            val partsArray = JSONArray()

            // Texto siempre primero
            partsArray.put(JSONObject().apply {
                put("text", prompt)
            })

            // Imagen si existe
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
                    JSONObject().apply {
                        put("parts", partsArray)
                    }
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                })
            }

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$modelo:generateContent?key=$apiKey"
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
                val errorBody = connection.errorStream
                    ?.bufferedReader()?.use { it.readText() } ?: "Sin detalle"
                return "❌ Error $responseCode [$modelo]: $errorBody"
            }

            val responseText = connection.inputStream
                .bufferedReader().use { it.readText() }

            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

        } catch (e: Exception) {
            "❌ Error [$modelo]: ${e.message}"
        }
    }
}
