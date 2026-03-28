package com.gustavo.chefvisionia

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class EventoFamiliar(
    val nombre: String,
    val relacion: String,
    val dia: Int,
    val mes: Int,
    val gustos: String,
    val ultimoPlatillo: String? = null
)

object EventMemoryManager {

    private const val PREFS_NAME = "chef_vision_family_memory"
    private const val KEY_EVENTS = "lista_eventos"

    fun guardarEvento(context: Context, evento: EventoFamiliar) {
        val eventos = obtenerTodos(context).toMutableList()
        eventos.removeAll { it.nombre == evento.nombre }
        eventos.add(evento)
        val json = Gson().toJson(eventos)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_EVENTS, json).apply()
    }

    fun obtenerTodos(context: Context): List<EventoFamiliar> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        val type = object : TypeToken<List<EventoFamiliar>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun buscarEventoCercano(context: Context): EventoFamiliar? {
        val hoy = java.util.Calendar.getInstance()
        val mesActual = hoy.get(java.util.Calendar.MONTH) + 1
        val diaActual = hoy.get(java.util.Calendar.DAY_OF_MONTH)
        val eventos = obtenerTodos(context)

        return eventos.find { evento ->
            val diasHastaEvento = when {
                evento.mes == mesActual -> evento.dia - diaActual
                evento.mes == mesActual + 1 -> (30 - diaActual) + evento.dia
                else -> -1
            }
            diasHastaEvento in 0..7
        }
    }

    fun obtenerMensajeEvento(evento: EventoFamiliar): String {
        val diasRestantes = calcularDiasRestantes(evento)
        return when {
            diasRestantes == 0 ->
                "🎉 ¡HOY es el cumpleaños de ${evento.nombre}! ¿Hacemos algo especial? Le encanta: ${evento.gustos}"
            diasRestantes == 1 ->
                "🎂 ¡Mañana es el cumpleaños de ${evento.nombre}! Prepara algo rico. Le gusta: ${evento.gustos}"
            diasRestantes <= 7 ->
                "💡 En $diasRestantes días es el cumpleaños de ${evento.nombre}. ¿Empezamos a planear? Le gusta: ${evento.gustos}"
            else -> ""
        }
    }

    private fun calcularDiasRestantes(evento: EventoFamiliar): Int {
        val hoy = java.util.Calendar.getInstance()
        val mesActual = hoy.get(java.util.Calendar.MONTH) + 1
        val diaActual = hoy.get(java.util.Calendar.DAY_OF_MONTH)

        return when {
            evento.mes == mesActual -> evento.dia - diaActual
            evento.mes == mesActual + 1 -> (30 - diaActual) + evento.dia
            else -> 99
        }
    }

    fun initFamilia(context: Context) {
        if (obtenerTodos(context).isEmpty()) {
            guardarEvento(
                context, EventoFamiliar(
                    nombre = "Denisse",
                    relacion = "Hija",
                    dia = 21,
                    mes = 10,
                    gustos = "Chocolate, fresas y postres coloridos"
                )
            )
            guardarEvento(
                context, EventoFamiliar(
                    nombre = "Daniel",
                    relacion = "Hijo",
                    dia = 27,
                    mes = 2,
                    gustos = "Pizza, tacos y comida mexicana"
                )
            )
            guardarEvento(
                context, EventoFamiliar(
                    nombre = "Andrés",
                    relacion = "Hijo",
                    dia = 23,
                    mes = 9,
                    gustos = "Comida mexicana picante y tamales"
                )
            )
        }
    }
}
