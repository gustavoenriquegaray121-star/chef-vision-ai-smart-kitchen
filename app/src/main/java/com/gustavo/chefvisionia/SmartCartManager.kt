package com.gustavo.chefvisionia

object SmartCartManager {

    // Ingredientes base por receta
    private val baseRecetas = mapOf(
        "Omelette con tocino"    to listOf("huevo", "tocino", "cebolla"),
        "Huevos a la mexicana"   to listOf("huevo", "tomate", "cebolla", "chile"),
        "Huevos con tocino"      to listOf("huevo", "tocino"),
        "Huevo estrellado"       to listOf("huevo"),
        "Papas con tocino"       to listOf("papa", "tocino"),
        "Papas con huevo"        to listOf("papa", "huevo"),
        "Papas fritas"           to listOf("papa"),
        "Huevo con chorizo"      to listOf("huevo", "chorizo"),
        "Ensalada básica"        to listOf("lechuga", "tomate"),
        "Sándwich sencillo"      to listOf("pan", "jamón")
    )

    fun detectarFaltantes(
        receta: String,
        ingredientesUsuario: List<String>
    ): List<String> {
        val necesarios = baseRecetas[receta] ?: return emptyList()

        val usuarioLower = ingredientesUsuario.map { it.lowercase().trim() }

        // BUG 3 FIX: match flexible — "chile serrano" hace match con "chile"
        return necesarios.filter { necesario ->
            val necesarioLower = necesario.lowercase().trim()
            // El usuario tiene este ingrediente si alguno de los suyos CONTIENE el necesario
            // o el necesario CONTIENE alguno del usuario
            usuarioLower.none { disponible ->
                disponible.contains(necesarioLower) || necesarioLower.contains(disponible)
            }
        }
    }
}
