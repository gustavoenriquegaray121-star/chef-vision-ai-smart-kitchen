package com.gustavo.chefvisionia

import android.content.Context

object SmartCartManager {

    // Ingredientes básicos que debería tener una receta
    private val baseRecetas = mapOf(
        "Omelette con tocino" to listOf("huevo", "tocino", "cebolla"),
        "Huevos a la mexicana" to listOf("huevo", "tomate", "cebolla", "chile"),
        "Papas con tocino" to listOf("papa", "tocino", "sal")
    )

    fun detectarFaltantes(
        receta: String,
        ingredientesUsuario: List<String>
    ): List<String> {

        val necesarios = baseRecetas[receta] ?: return emptyList()

        return necesarios.filter {
            !ingredientesUsuario.contains(it)
        }
    }
}
