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

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun detectarIngredientes(bitmap: Bitmap): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(bitmap)

                val prompt = """
                    Analiza esta imagen y lista SOLO los ingredientes alimenticios que ves.
                    Responde ÚNICAMENTE con una lista separada por comas.
                    Ejemplo: huevo, tocino, cebolla, tomate
                    No agregues explicaciones ni texto extra.
                """.trimIndent()

                val respuesta = llamarGemini(base64Image, prompt)
                respuesta.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun generarReceta(
        nombreReceta: String,
        ingredientesDisponibles: List<String>,
        idioma: String = "español"
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Eres Chef Vision IA, el mejor chef del mundo.
                    
                    El usuario quiere preparar: $nombreReceta
                    Ingredientes que tiene: ${ingredientesDisponibles.joinToString(", ")}
                    
                    Responde en $idioma con:
                    
                    🍽️ RECETA: $nombreReceta
                    
                    ⏱️ Tiempo: (minutos)
                    
                    📦 Ingredientes necesarios:
                    (lista)
                    
                    👨‍🍳 Pasos:
                    1.
                    2.
                    3.
                    
                    💡 Tip del Chef:
                    (consejo profesional)
                    
                    🔄 Sustituciones posibles:
                    (si falta algo, qué puede usar en su lugar)
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "❌ Error generando receta: ${e.message}"
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
                    Soy un chef profesional. 
                    El usuario no tiene: $ingredienteFaltante
                    Lo que sí tiene: ${ingredientesDisponibles.joinToString(", ")}
                    
                    Sugiere en máximo 2 líneas qué puede usar como sustituto 
                    y qué tan bien quedará el plato (porcentaje de sabor).
                    Sé directo y práctico.
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "No hay sustitución disponible"
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
                    El usuario tiene $ingrediente que lleva $diasEnRefri días en el refrigerador.
                    En máximo 2 líneas dile:
                    1. Si debe usarlo hoy o cuántos días más aguanta
                    2. La mejor receta express para no desperdiciarlo
                    Sé directo y amigable.
                """.trimIndent()

                llamarGemini(null, prompt)

            } catch (e: Exception) {
                "Revisa el estado de tu $ingrediente"
            }
        }
    }

    private fun llamarGemini(base64Image: String?, prompt: String): String {
        val partes = JSONArray()

        if (base64Image != null) {
            partes.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                })
            })
        }

        partes.put(JSONObject().apply {
            put("text", prompt)
        })

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", partes)
                }
            ))
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
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
        val responseText = if (responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            return "❌ Error $responseCode: ${connection.errorStream?.bufferedReader()?.readText()}"
        }

        return try {
            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            "❌ Error procesando respuesta"
        }
    }
}
