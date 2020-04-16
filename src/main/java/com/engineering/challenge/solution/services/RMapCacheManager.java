package com.engineering.challenge.solution.services;

import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RMapCacheManager {

    private final RedissonClient redissonClient;

    private ConcurrentHashMap<String, RMapCache> caches = new ConcurrentHashMap<>();

    /**
     * According to https://github.com/redisson/redisson/wiki/7.-distributed-collections#eviction It's recommended to use single instance of
     * RMapCache object with the same name and bounded to the same Redisson client.
     */
    public RMapCache getCache(String name) {
        RMapCache cache = caches.get(name);
        if (cache == null) {
            cache = redissonClient.getMapCache(name);
            caches.putIfAbsent(name, cache);
        }
        return cache;
    }

}
