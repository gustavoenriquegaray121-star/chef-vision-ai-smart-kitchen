package com.gustavo.chefvisionia.core

import android.content.Context

object MemoryManager {

    fun guardar(context: Context, lista: List<String>) {
        val prefs = context.getSharedPreferences("chef_memory", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("ingredientes", lista.toSet()).apply()
    }

    fun obtener(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("chef_memory", Context.MODE_PRIVATE)
        return prefs.getStringSet("ingredientes", emptySet()) ?: emptySet()
    }
}
