package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointConcurrencyIntegrationTest {

    @Autowired
    private PointService pointService;

    @Test
    void 동시에_여러번_충전해도_정확히_누적된다() throws Exception {
        // 이유: 동시 충전 상황에서 누락/덮어쓰기 없이 요청 수만큼 정확히 반영되는지 검증해야 동시성 제어가 유효함을 확인할 수 있다.
        long userId = uniqueUserId();
        int requestCount = 10;
        long chargeAmount = 100L;

        runConcurrently(requestCount, () -> pointService.charge(userId, chargeAmount));

        UserPoint result = pointService.getPoint(userId);
        List<PointHistory> histories = pointService.getHistories(userId);

        assertThat(result.point()).isEqualTo(requestCount * chargeAmount);
        assertThat(histories).hasSize(requestCount);
        assertThat(histories).allMatch(history -> history.type() == TransactionType.CHARGE);
    }

    @Test
    void 동시에_여러번_사용하면_잔고를_초과해서_사용되지_않는다() throws Exception {
        // 이유: 사용 요청이 동시 발생해도 순차적으로 처리되어야 초과 차감(음수 잔액)과 이력 불일치를 막을 수 있다.
        long userId = uniqueUserId();
        int requestCount = 10;
        long useAmount = 200L;

        pointService.charge(userId, 1_000L);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        runConcurrently(requestCount, () -> {
            try {
                pointService.use(userId, useAmount);
                successCount.incrementAndGet();
            } catch (IllegalArgumentException e) {
                failureCount.incrementAndGet();
            }
        });

        UserPoint result = pointService.getPoint(userId);
        List<PointHistory> histories = pointService.getHistories(userId);

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);
        assertThat(result.point()).isEqualTo(0L);
        assertThat(histories).hasSize(6);
    }

    private long uniqueUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    }

    private void runConcurrently(int requestCount, CheckedRunnable task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                task.run();
                return null;
            }));
        }

        if (!readyLatch.await(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
            throw new IllegalStateException("동시 실행 준비 시간이 초과되었습니다.");
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executorService.shutdown();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
