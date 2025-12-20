package com.dam2.flashdownloader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform