package com.gustavo.chefvisionia

import android.content.Context

object MemoryManager {

    private const val PREFS_NAME = "chef_memory"
    private const val KEY_INGREDIENTES = "ingredientes"

    fun guardar(context: Context, lista: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_INGREDIENTES, lista.toSet()).apply()
    }

    fun obtener(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_INGREDIENTES, emptySet())?.toList() ?: emptyList()
    }

    fun agregar(context: Context, ingrediente: String) {
        val actual = obtener(context).toMutableList()
        actual.add(ingrediente)
        guardar(context, actual)
    }

    fun limpiar(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
