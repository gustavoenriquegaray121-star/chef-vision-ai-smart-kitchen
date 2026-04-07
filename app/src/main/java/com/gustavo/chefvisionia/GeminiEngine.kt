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

    // ─── Modelos actualizados según lista real de la API ──────────────────────
    private val modelos = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite"
    )

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Calidad 60% para reducir tamaño y evitar timeout
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val bytes = outputStream.toByteArray()
        Log.d(TAG, "Bitmap bytes: ${bytes.size} — base64 chars aprox: ${bytes.size * 4 / 3}")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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
                Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}")

                // ─── Reducir resolución para evitar timeout ────────────────
                val bitmapFinal = if (bitmap.width > 384 || bitmap.height > 384) {
                    Bitmap.createScaledBitmap(bitmap, 384, 384, true)
                } else bitmap

                val base64Image = bitmapToBase64(bitmapFinal)
                Log.d(TAG, "Imagen convertida OK — base64 length: ${base64Image.length}")

                val contextoExtra = if (contextoFamiliar.isNotEmpty())
                    "\n\nContexto especial: $contextoFamiliar"
                else ""

                val prompt = """
                    Eres Chef Vision IA. Analiza esta imagen.
                    
                    Identifica TODO lo comestible que veas:
                    - Ingredientes sueltos (verduras, frutas, carnes, lácteos)
                    - Empaques de supermercado (tocino, jamón, queso, papas, galletas)
                    - Comida preparada o platillos
                    - Bebidas o jugos
                    
                    Ejemplos:
                    - Empaque Sabritas = papas fritas
                    - Empaque Oreo = galletas
                    - Del Valle durazno = jugo de durazno
                    - Bubulubu = chocolate
                    - Tacos = tortilla, carne, cebolla, cilantro
                    - Bimbuñuelos = pan dulce
                    
                    Responde SOLO con los ingredientes separados por comas en español.
                    NO expliques nada. Solo la lista.
                    Ejemplo: papas fritas, galletas, jugo de durazno
                    
                    Si NO hay absolutamente nada comestible responde: NINGUNO$contextoExtra
                """.trimIndent()

                val respuesta = llamarGeminiConFallback(base64Image, prompt)
                Log.d(TAG, "Respuesta raw: $respuesta")

                if (respuesta.startsWith("❌") ||
                    respuesta.contains("NINGUNO", ignoreCase = true)) {
                    Log.d(TAG, "Sin ingredientes detectados")
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
                    "\n\n💖 Contexto especial: $contextoFamiliar."
                else ""

                val prompt = """
                    Eres Chef Vision IA, el mejor chef y amigo de la familia.
                    
                    El usuario quiere preparar: $nombreReceta
                    Ingredientes disponibles: ${ingredientesDisponibles.joinToString(", ")}
                    $contextoExtra
                    
                    Responde en $idioma con este formato:
                    
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
                    Chef profesional y práctico.
                    NO tiene: $ingredienteFaltante
                    SÍ tiene: ${ingredientesDisponibles.joinToString(", ")}
                    En máximo 2 líneas: sustituto y % de sabor resultante.
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
                    $ingrediente lleva $diasEnRefri días en el refri.
                    En 2 líneas: ¿cuántos días más aguanta? y mejor receta express.
                """.trimIndent()

                llamarGeminiConFallback(null, prompt)

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
            Log.d(TAG, "Intentando modelo: $modelo")
            val resultado = llamarGemini(base64Image, prompt, modelo)
            Log.d(TAG, "Resultado [$modelo]: ${resultado.take(300)}")
            if (!resultado.startsWith("❌")) {
                Log.d(TAG, "✅ Modelo exitoso: $modelo")
                return resultado
            }
            ultimoError = resultado
            Log.e(TAG, "❌ Modelo falló [$modelo]: $ultimoError")
        }
        Log.e(TAG, "❌ TODOS LOS MODELOS FALLARON. Último: $ultimoError")
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
                    put("temperature", 0.4)
                    put("maxOutputTokens", 256)
                })
            }

            val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$modelo:generateContent?key=$apiKey"

            Log.d(TAG, "Llamando: $urlStr")

            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            // ─── Timeouts aumentados ──────────────────────────────────────
            connection.connectTimeout = 60000
            connection.readTimeout = 60000

            val bodyStr = requestBody.toString()
            Log.d(TAG, "Request body size: ${bodyStr.length} chars")

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(bodyStr)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code [$modelo]: $responseCode")

            if (responseCode != 200) {
                val errorBody = connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: "Sin detalle de error"
                Log.e(TAG, "Error HTTP [$modelo] $responseCode: $errorBody")
                return "❌ Error $responseCode: $errorBody"
            }

            val responseText = connection.inputStream
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            Log.d(TAG, "Response OK [$modelo] length: ${responseText.length}")

            val json = JSONObject(responseText)
            val candidates = json.getJSONArray("candidates")

            if (candidates.length() == 0) {
                Log.e(TAG, "Sin candidatos en respuesta")
                return "❌ Sin candidatos"
            }

            val text = candidates
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            Log.d(TAG, "Texto extraído: $text")
            text

        } catch (e: Exception) {
            Log.e(TAG, "EXCEPCIÓN HTTP [$modelo]: ${e.message}", e)
            "❌ Excepción: ${e.message}"
        }
    }
}
