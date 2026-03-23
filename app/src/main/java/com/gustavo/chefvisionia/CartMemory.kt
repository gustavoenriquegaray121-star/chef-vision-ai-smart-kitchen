package com.gustavo.chefvisionia

import android.content.Context

object CartMemory {

    private const val PREFS = "cart_memory"
    private const val KEY = "lista"

    fun agregarLista(context: Context, items: List<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val actual = prefs.getStringSet(KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        actual.addAll(items)

        prefs.edit().putStringSet(KEY, actual).apply()
    }

    fun obtenerLista(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY, mutableSetOf())?.toMutableList() ?: mutableListOf()
    }

    fun limpiar(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
