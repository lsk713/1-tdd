package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointControllerTest {

    private final PointService pointService = mock(PointService.class);
    private final PointController pointController = new PointController(pointService);

    @Test
    void 포인트_조회_API는_서비스_결과를_반환한다() {
        // 이유: 컨트롤러 TODO 기능의 핵심은 올바른 서비스 호출/응답 전달이므로 조회 엔드포인트 위임을 검증한다.
        long userId = 1L;
        UserPoint expected = new UserPoint(userId, 1_000L, 10L);
        when(pointService.getPoint(userId)).thenReturn(expected);

        UserPoint result = pointController.point(userId);

        assertThat(result).isEqualTo(expected);
        verify(pointService).getPoint(userId);
    }

    @Test
    void 포인트_내역_조회_API는_서비스_결과를_반환한다() {
        // 이유: 내역 조회 엔드포인트가 사용자별 히스토리를 누락 없이 전달하는지 보장하기 위해 위임 동작을 확인한다.
        long userId = 2L;
        List<PointHistory> expected = List.of(new PointHistory(1L, userId, 500L, TransactionType.CHARGE, 10L));
        when(pointService.getHistories(userId)).thenReturn(expected);

        List<PointHistory> result = pointController.history(userId);

        assertThat(result).isEqualTo(expected);
        verify(pointService).getHistories(userId);
    }

    @Test
    void 포인트_충전_API는_서비스_결과를_반환한다() {
        // 이유: 충전 엔드포인트는 요청 금액을 그대로 서비스에 전달해야 하므로 파라미터 전달 정확성을 검증한다.
        long userId = 3L;
        long amount = 700L;
        UserPoint expected = new UserPoint(userId, 1_700L, 20L);
        when(pointService.charge(userId, amount)).thenReturn(expected);

        UserPoint result = pointController.charge(userId, amount);

        assertThat(result).isEqualTo(expected);
        verify(pointService).charge(userId, amount);
    }

    @Test
    void 포인트_사용_API는_서비스_결과를_반환한다() {
        // 이유: 사용 엔드포인트도 충전과 동일하게 도메인 규칙을 서비스에 위임하므로 호출 연결이 올바른지 검증한다.
        long userId = 4L;
        long amount = 300L;
        UserPoint expected = new UserPoint(userId, 700L, 30L);
        when(pointService.use(userId, amount)).thenReturn(expected);

        UserPoint result = pointController.use(userId, amount);

        assertThat(result).isEqualTo(expected);
        verify(pointService).use(userId, amount);
    }
}
