package com.gustavo.chefvisionia

import android.content.Context

object MemoryManager {

    private const val PREFS = "chef_memory"
    private const val KEY_INGREDIENTES = "ingredientes"

    fun guardar(context: Context, lista: List<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_INGREDIENTES, lista.toSet()).apply()
    }

    fun obtener(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_INGREDIENTES, emptySet()) ?: emptySet()
    }
}
