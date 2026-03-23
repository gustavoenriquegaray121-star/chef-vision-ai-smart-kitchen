package com.gustavo.chefvisionia

object RecipeEngine {

    fun generarOpciones(ingredientes: List<String>): List<String> {

        val opciones = mutableListOf<String>()

        if (ingredientes.contains("huevo")) {
            if (ingredientes.contains("tocino")) {
                opciones.add("Omelette con tocino")
                opciones.add("Huevos con tocino")
            }

            if (ingredientes.contains("cebolla") && ingredientes.contains("tomate")) {
                opciones.add("Huevos a la mexicana")
            }

            opciones.add("Huevo estrellado")
        }

        if (ingredientes.contains("papa")) {
            opciones.add("Papas fritas")
            if (ingredientes.contains("huevo")) {
                opciones.add("Papas con huevo")
            }
        }

        if (ingredientes.contains("chorizo")) {
            opciones.add("Huevo con chorizo")
        }

        if (opciones.isEmpty()) {
            opciones.add("Ensalada básica")
            opciones.add("Sándwich sencillo")
        }

        return opciones
    }
}
