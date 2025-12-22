package com.dam2.flashdownloader.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Interface for bandwidth limiting
 */
interface BandwidthLimiter {
    suspend fun acquire(bytes: Int)
    fun setRate(bytesPerSecond: Long)
}

/**
 * Implementation of Token Bucket algorithm for smooth speed limiting.
 * Uses a "debt" model where tokens can go negative, requiring subsequent callers (or the current one) to wait.
 */
class TokenBucketLimiter(initialRate: Long, private val maxBurstBytes: Long = 10 * 1024 * 1024) : BandwidthLimiter {

    private var rate = initialRate // Bytes per second
    private var tokens = 0.0
    private var lastRefillTime = System.currentTimeMillis()
    private val mutex = Mutex()

    override fun setRate(bytesPerSecond: Long) {
        // Update rate safely. We don't lock for this simple assignment as it's volatile-ish logic 
        // and exact precision during rate change isn't critical.
        this.rate = bytesPerSecond
    }

    override suspend fun acquire(bytes: Int) {
        // If rate is 0 or negative, we treat it as unlimited
        if (rate <= 0) return

        var delayTime = 0L

        mutex.withLock {
            // 1. Refill tokens based on time passed
            val now = System.currentTimeMillis()
            if (lastRefillTime > 0) {
                val duration = now - lastRefillTime
                if (duration > 0) {
                    val newTokens = (duration * rate) / 1000.0
                    // Cap tokens at maxBurst (prevent huge accumulation during idle)
                    tokens = min(tokens + newTokens, maxBurstBytes.toDouble())
                }
            }
            lastRefillTime = now

            // 2. Consume tokens
            if (tokens >= bytes) {
                // Happy path: have enough tokens
                tokens -= bytes
            } else {
                // Not enough tokens: take them anyway (go into debt) and calculate wait
                val missing = bytes - tokens
                tokens -= bytes // tokens is now negative
                
                // Calculate time needed to pay back the debt we just incurred
                delayTime = (missing * 1000 / rate).toLong()
            }
        }

        // 3. Wait if needed
        if (delayTime > 0) {
            delay(delayTime)
        }
    }
}

/**
 * Composite limiter that enforces multiple limits simultaneously (e.g. Global + Per-Download)
 */
class CompositeBandwidthLimiter(private val limiters: List<BandwidthLimiter>) : BandwidthLimiter {
    
    constructor(vararg limiters: BandwidthLimiter) : this(limiters.toList())

    override suspend fun acquire(bytes: Int) {
        // Acquire from all limiters. The implementation of TokenBucket ensures that
        // waiting in one allows others to refill, so sequential acquisition works correctly
        // to enforce the strictest limit.
        limiters.forEach { it.acquire(bytes) }
    }

    override fun setRate(bytesPerSecond: Long) {
        // Not supported for composite, as it manages multiple distinct limiters
        // Individual limiters should be updated directly
    }
}
