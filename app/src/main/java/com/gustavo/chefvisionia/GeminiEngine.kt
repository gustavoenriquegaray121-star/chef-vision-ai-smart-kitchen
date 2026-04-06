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
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest"
    )

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun detectarIngredientes(
        bitmap: Bitmap,
        contextoFamiliar: String = ""
    ): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "=== INICIO DETECCIÓN ===")
                Log.d(TAG, "API Key vacía: ${apiKey.isEmpty()}")
                Log.d(TAG, "API Key longitud: ${apiKey.length}")
                Log.d(TAG, "Contexto: $contextoFamiliar")

                val base64Image = bitmapToBase64(bitmap)
                Log.d(TAG, "Imagen convertida OK — tamaño: ${base64Image.length} chars")

                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\n\nContexto especial: $contextoFamiliar"
                else ""

                val prompt = """
                    Eres Chef Vision IA, el asistente culinario más inteligente del mundo.
                    Analiza esta imagen con máxima atención.
                    
                    Puedes ver:
                    - Ingredientes sueltos (verduras, frutas, carnes, lácteos)
                    - Empaques o productos de supermercado (tocino, jamón, queso, etc.)
                    - Comida preparada o platillos
                    - Refri o despensa con varios productos
                    
                    Tu tarea: identificar TODO lo que sea comestible o ingrediente.
                    Si ves un empaque de tocino FUD, responde: tocino
                    Si ves una crema Alpura, responde: crema
                    Si ves huevos, responde: huevo
                    Si ves tacos, responde: tortilla, carne, cebolla, cilantro
                    Si ves pan dulce Bimbo, responde: pan dulce, azúcar
                    
                    Responde ÚNICAMENTE con los ingredientes separados por comas,
                    en español y en minúsculas, sin explicaciones.
                    Ejemplo: tocino, crema, huevo
                    
                    Solo si la imagen no tiene absolutamente nada relacionado
                    con comida, responde: NINGUNO$contextoExtra
                """.trimIndent()

                val respuesta = llamarGeminiConFallback(base64Image, prompt)

                Log.d(TAG, "Respuesta raw: $respuesta")

                if (respuesta.startsWith("❌") ||
                    respuesta.contains("NINGUNO", ignoreCase = true)) {
                    Log.d(TAG, "Respuesta vacía o NINGUNO — lista vacía")
                    return@withContext emptyList()
                }

                val ingredientes = respuesta
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && it.length > 1 }

                Log.d(TAG, "Ingredientes detectados: $ingredientes")
                ingredientes

            } catch (e: Exception) {
                Log.e(TAG, "EXCEPCIÓN: ${e.message}", e)
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

    private fun llamarGeminiConFallback(
        base64Image: String?,
        prompt: String
    ): String {
        var ultimoError = ""
        for (modelo in modelos) {
            Log.d(TAG, "Intentando modelo: $modelo")
            val resultado = llamarGemini(base64Image, prompt, modelo)
            Log.d(TAG, "Resultado [$modelo]: ${resultado.take(200)}")
            if (!resultado.startsWith("❌")) {
                Log.d(TAG, "✅ Modelo exitoso: $modelo")
                return resultado
            }
            ultimoError = resultado
        }
        Log.e(TAG, "❌ Todos los modelos fallaron. Último error: $ultimoError")
        return ultimoError
    }

    private fun llamarGemini(
        base64Image: String?,
        prompt: String,
        modelo: String = modelos.first()
    ): String {
        return try {
            val partsArray = JSONArray()

            partsArray.put(JSONObject().apply {
                put("text", prompt)
            })

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

            val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$modelo:generateContent?key=$apiKey"

            Log.d(TAG, "URL modelo: $modelo")

            val url = URL(urlStr)
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
            Log.d(TAG, "Response code [$modelo]: $responseCode")

            if (responseCode != 200) {
                val errorBody = connection.errorStream
                    ?.bufferedReader()?.use { it.readText() } ?: "Sin detalle"
                Log.e(TAG, "Error HTTP [$modelo] $responseCode: $errorBody")
                return "❌ Error $responseCode [$modelo]: $errorBody"
            }

            val responseText = connection.inputStream
                .bufferedReader().use { it.readText() }

            Log.d(TAG, "Response OK [$modelo] — longitud: ${responseText.length}")

            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

        } catch (e: Exception) {
            Log.e(TAG, "EXCEPCIÓN HTTP [$modelo]: ${e.message}", e)
            "❌ Error [$modelo]: ${e.message}"
        }
    }
}
