package com.opencalori.app.domain.model

import java.util.Locale

/**
 * The one place where food names are folded before they are compared.
 *
 * Russian users type ё and е interchangeably and models answer in whatever case they like, so
 * «Гречка», «гречка» and «грёчка» must be one and the same product for every matcher:
 * the response parser, the local catalogue resolver and the dish resolver.
 */
object FoodNameMatching {

    /** Trim, lowercase, ё -> е. Nothing else, so callers can add their own steps on top. */
    fun fold(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')
}
