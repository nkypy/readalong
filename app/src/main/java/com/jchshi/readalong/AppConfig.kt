package com.jchshi.readalong

data class WebEntry(
    val title: String,
    val url: String,
)

object AppConfig {
    val sites = listOf(
        WebEntry("Read Along", "https://readalong.google.com/"),
    )

    const val defaultSiteIndex = 0
}
