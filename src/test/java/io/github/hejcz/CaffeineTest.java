package io.github.hejcz;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaffeineTest {

    ListeningExecutorService cacheExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2,
            new ThreadFactoryBuilder()
                    .setNameFormat("resource-manager" + "-thread-%d")
                    .setDaemon(true)
                    .build()));

    LoadingCache<String, InvalidableResource> cache = Caffeine.newBuilder()
            .<String, InvalidableResource>removalListener((key, value, cause) -> {
                if (value != null) {
                    value.invalidate(cause);
                }
            })
            .refreshAfterWrite(Duration.ofSeconds(1))
            .executor(cacheExecutorService)
            .build(new CacheLoader<>() {

                private InvalidableResource invalidableResource = new InvalidableResource();

                @Override
                public @Nullable InvalidableResource load(String key) throws Exception {
                    System.out.println("reload: " + System.currentTimeMillis());
                    return invalidableResource;
                }
            });

    @Test
    void name() throws InterruptedException {
        Random random = new Random();

        String[] values = {"a", "b", "c", "d", "e", "f", "g", "h"};

        ExecutorService executorService = Executors.newFixedThreadPool(8);

        for (int i = 0; i < 8; i++) {
            executorService.submit(() -> {
                while (true) {
                    try {
                        while (true) {
                            int key = random.nextInt(8);
                            InvalidableResource invalidableResource = cache.get(values[key]);
                            invalidableResource.use();
                        }
                    } catch (IllegalStateException exception) {
                        exception.printStackTrace();
                    }
                }
            });
        }

        Thread.sleep(10_000);
    }

    class InvalidableResource {

        private AtomicBoolean invalid = new AtomicBoolean(false);

        synchronized void use() {
            if (invalid.get()) {
                throw new IllegalStateException("resource is invalidated");
            }
        }

        synchronized void invalidate(RemovalCause cause) {
            System.out.println(Thread.currentThread().getName());
            System.out.println(cause);
            System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
            this.invalid.set(true);
        }

    }
}
