package com.example.datadomeapp.teacher

class MetadataCache {
    private var cache: Map<String, ActivityMetadata> = emptyMap()
    private var lastFetchTime: Long = 0

    // Remove 'const' and use regular val
    private val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes

    fun isCacheValid(): Boolean {
        return cache.isNotEmpty() && System.currentTimeMillis() - lastFetchTime < CACHE_DURATION
    }

    fun getCache(): Map<String, ActivityMetadata> = cache

    fun updateCache(newCache: Map<String, ActivityMetadata>) {
        cache = newCache
        lastFetchTime = System.currentTimeMillis()
    }

    fun clear() {
        cache = emptyMap()
        lastFetchTime = 0
    }
}

class StudentScoresCache {
    private val cache = mutableMapOf<String, DetailedScores>()
    private val accessTimes = mutableMapOf<String, Long>()

    // Remove 'const' and use regular val
    private val MAX_CACHE_SIZE = 50
    private val CACHE_DURATION = 10 * 60 * 1000L // 10 minutes

    fun get(studentId: String): DetailedScores? {
        return cache[studentId]?.also {
            accessTimes[studentId] = System.currentTimeMillis()
        }
    }

    fun put(studentId: String, scores: DetailedScores) {
        // Clean old entries if cache is full
        if (cache.size >= MAX_CACHE_SIZE) {
            val oldest = accessTimes.minByOrNull { it.value }?.key
            oldest?.let {
                cache.remove(it)
                accessTimes.remove(it)
            }
        }
        cache[studentId] = scores
        accessTimes[studentId] = System.currentTimeMillis()
    }

    fun remove(studentId: String) {
        cache.remove(studentId)
        accessTimes.remove(studentId)
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = accessTimes.filter { now - it.value > CACHE_DURATION }.keys
        expired.forEach {
            cache.remove(it)
            accessTimes.remove(it)
        }
    }

    fun clear() {
        cache.clear()
        accessTimes.clear()
    }
}