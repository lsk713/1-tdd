package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final Object lock = new Object();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint getPoint(long id) {
        synchronized (lock) {
            return userPointTable.selectById(id);
        }
    }

    public List<PointHistory> getHistories(long id) {
        synchronized (lock) {
            return pointHistoryTable.selectAllByUserId(id);
        }
    }

    public UserPoint charge(long id, long amount) {
        synchronized (lock) {
            validateAmount(amount);
            UserPoint current = userPointTable.selectById(id);
            UserPoint updated = userPointTable.insertOrUpdate(id, current.point() + amount);
            pointHistoryTable.insert(id, amount, TransactionType.CHARGE, updated.updateMillis());
            return updated;
        }
    }

    public UserPoint use(long id, long amount) {
        synchronized (lock) {
            validateAmount(amount);
            UserPoint current = userPointTable.selectById(id);
            if (current.point() < amount) {
                throw new IllegalArgumentException("잔고가 부족합니다.");
            }
            UserPoint updated = userPointTable.insertOrUpdate(id, current.point() - amount);
            pointHistoryTable.insert(id, amount, TransactionType.USE, updated.updateMillis());
            return updated;
        }
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트는 1 이상이어야 합니다.");
        }
    }
}
