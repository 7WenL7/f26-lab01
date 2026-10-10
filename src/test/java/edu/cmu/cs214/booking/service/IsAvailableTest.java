package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class IsAvailableTest {

    @Test
    void notAvailableWhenAnEarlierBookingRunsIntoTheRequest() {
        // 手动构造所需的对象，不依赖其它测试类的私有方法
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);

        Room roomA = new Room("r1", "Room A", 10);
        User alice = new User("u1", "Alice");

        // 已有预订 [570, 630)
        svc.book(roomA, alice, new TimeInterval(570, 630));

        // 请求 [600, 660)，明显重叠，isAvailable 应该返回 false
        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }
}