package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    @Test
    void 사용자_포인트를_조회한다() {
        // 이유: 포인트 조회 기능의 핵심은 저장소 값을 그대로 반환하는 것이므로, 조회 위임이 정확한지 검증한다.
        long userId = 1L;
        UserPoint expected = new UserPoint(userId, 3_000L, 100L);
        when(userPointTable.selectById(userId)).thenReturn(expected);

        UserPoint result = pointService.getPoint(userId);

        assertThat(result).isEqualTo(expected);
        verify(userPointTable).selectById(userId);
    }

    @Test
    void 사용자_포인트_내역을_조회한다() {
        // 이유: 내역 조회 기능은 데이터 변형 없이 사용자별 이력 목록을 제공해야 하므로, 반환값과 위임 호출을 함께 검증한다.
        long userId = 2L;
        List<PointHistory> expected = List.of(
                new PointHistory(1L, userId, 1_000L, TransactionType.CHARGE, 10L),
                new PointHistory(2L, userId, 300L, TransactionType.USE, 20L)
        );
        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expected);

        List<PointHistory> result = pointService.getHistories(userId);

        assertThat(result).isEqualTo(expected);
        verify(pointHistoryTable).selectAllByUserId(userId);
    }

    @Test
    void 포인트를_충전한다() {
        // 이유: 충전은 잔액 증가와 CHARGE 이력 저장이 반드시 함께 일어나야 하므로 두 결과를 동시에 검증한다.
        long userId = 3L;
        long chargeAmount = 500L;
        UserPoint current = new UserPoint(userId, 1_000L, 100L);
        UserPoint updated = new UserPoint(userId, 1_500L, 200L);
        when(userPointTable.selectById(userId)).thenReturn(current);
        when(userPointTable.insertOrUpdate(userId, 1_500L)).thenReturn(updated);

        UserPoint result = pointService.charge(userId, chargeAmount);

        assertThat(result).isEqualTo(updated);
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, 1_500L);
        verify(pointHistoryTable).insert(userId, chargeAmount, TransactionType.CHARGE, updated.updateMillis());
    }

    @Test
    void 포인트를_사용한다() {
        // 이유: 사용 성공 시 잔액 감소와 USE 이력 저장이 정확히 수행되어야 하므로 계산 결과와 저장 호출을 검증한다.
        long userId = 4L;
        long useAmount = 700L;
        UserPoint current = new UserPoint(userId, 2_000L, 100L);
        UserPoint updated = new UserPoint(userId, 1_300L, 300L);
        when(userPointTable.selectById(userId)).thenReturn(current);
        when(userPointTable.insertOrUpdate(userId, 1_300L)).thenReturn(updated);

        UserPoint result = pointService.use(userId, useAmount);

        assertThat(result).isEqualTo(updated);
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, 1_300L);
        verify(pointHistoryTable).insert(userId, useAmount, TransactionType.USE, updated.updateMillis());
    }

    @Test
    void 잔고가_부족하면_포인트_사용에_실패한다() {
        // 이유: 요구사항의 핵심 예외 시나리오로, 실패 시 잔액과 이력이 변경되지 않아야 하므로 예외와 부수효과 부재를 같이 검증한다.
        long userId = 5L;
        UserPoint current = new UserPoint(userId, 100L, 100L);
        when(userPointTable.selectById(userId)).thenReturn(current);

        assertThatThrownBy(() -> pointService.use(userId, 300L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("잔고가 부족합니다.");

        verify(userPointTable).selectById(userId);
        verifyNoMoreInteractions(userPointTable);
        verifyNoInteractions(pointHistoryTable);
    }
}
