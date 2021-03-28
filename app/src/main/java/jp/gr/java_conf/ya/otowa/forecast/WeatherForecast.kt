// Copyright (c) 2021 YA-androidapp(https://github.com/YA-androidapp) All rights reserved.
package jp.gr.java_conf.ya.otowa.forecast

data class WeatherForecast(
    val publicTime: String? = "",
    val publicTime_format: String? = "",
    val title: String? = "",
    val link: String? = "",
    val description: Description? = Description(),
    val forecasts: List<Forecast>? = listOf(),
    val location: Location? = Location(),
    val copyright: Copyright? = Copyright()
) {
    data class Description(
        val text: String? = "",
        val publicTime: String? = "",
        val publicTime_format: String? = ""
    )

    data class Forecast(
        val date: String? = "",
        val dateLabel: String? = "",
        val telop: String? = "",
        val temperature: Temperature? = Temperature(),
        val chanceOfRain: ChanceOfRain? = ChanceOfRain(),
        val image: Image? = Image()
    ) {
        data class Temperature(
            val min: Min? = Min(),
            val max: Max? = Max()
        ) {
            data class Max(
                val celsius: String? = "",
                val fahrenheit: String? = ""
            )
            data class Min(
                val celsius: String? = "",
                val fahrenheit: String? = ""
            )
        }

        data class ChanceOfRain(
            val `00-06`: String? = "",
            val `06-12`: String? = "",
            val `12-18`: String? = "",
            val `18-24`: String? = "",
            val T00_06: String? = "",
            val T06_12: String? = "",
            val T12_18: String? = "",
            val T18_24: String? = ""
        )

        data class Image(
            val title: String? = "",
            val url: String? = "",
            val width: Int? = 0,
            val height: Int? = 0
        )
    }

    data class Location(
        val city: String? = "",
        val area: String? = "",
        val prefecture: String? = ""
    )

    data class Copyright(
        val link: String? = "",
        val title: String? = "",
        val image: Image? = Image(),
        val provider: List<Provider>? = listOf()
    ) {
        data class Image(
            val width: Int? = 0,
            val height: Int? = 0,
            val link: String? = "",
            val url: String? = "",
            val title: String? = ""
        )

        data class Provider(
            val link: String? = "",
            val name: String? = "",
            val note: String? = ""
        )
    }
}