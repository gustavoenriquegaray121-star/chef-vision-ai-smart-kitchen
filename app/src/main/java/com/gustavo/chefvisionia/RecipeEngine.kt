package com.gustavo.chefvisionia

object RecipeEngine {

    fun generarOpciones(ingredientes: List<String>): List<String> {

        val opciones = mutableListOf<String>()

        if (ingredientes.contains("huevo")) {
            opciones.add("Omelette con tocino 🥓")
            opciones.add("Huevos a la mexicana 🇲🇽")
        }

        if (ingredientes.contains("tocino")) {
            opciones.add("Tacos de tocino 🌮")
        }

        if (ingredientes.contains("cebolla")) {
            opciones.add("Cebolla caramelizada 🧅")
        }

        if (ingredientes.contains("huevo") && ingredientes.contains("cebolla")) {
            opciones.add("Huevos con cebolla 🍳")
        }

        return opciones
    }
}
