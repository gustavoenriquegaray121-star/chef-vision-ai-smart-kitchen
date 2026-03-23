package com.gustavo.chefvisionia.core

object RecipeEngine {

    fun generarOpciones(ingredientes: List<String>): List<String> {
        val opciones = mutableListOf<String>()

        if ("huevo" in ingredientes && "tocino" in ingredientes) {
            opciones.add("Omelette con tocino")
        }

        if ("huevo" in ingredientes && "cebolla" in ingredientes) {
            opciones.add("Huevos a la mexicana")
        }

        if ("papa" in ingredientes) {
            opciones.add("Papas con huevo")
        }

        return opciones
    }
}
