package com.example.nsearth

data class City(val name: String, val latitude: Float, val longitude: Float)

object CityData {
    val cities = listOf(
        City("Tokyo", 35.6895f, 139.6917f),
        City("London", 51.5074f, -0.1278f),
        City("New York", 40.7128f, -74.0060f),
        City("Sydney", -33.8688f, 151.2093f),
        City("Sao Paulo", -23.5505f, -46.6333f),
        City("Prague", 50.0755f, 14.4378f),
        City("Beijing", 39.9042f, 116.4074f),
        City("Cairo", 30.0444f, 31.2357f),
        City("Moscow", 55.7558f, 37.6173f),
        City("Mexico City", 19.4326f, -99.1332f)
    )
}
