package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class BookingServiceTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    @Test
    void bookConfirmsWhenRoomIsFree() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void bookWaitlistsWhenSlotIsTaken() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(630, 700));
        assertInstanceOf(BookingResult.Waitlisted.class, r);
    }

    @Test
    void backToBackBookingsAreConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomA, bob, new TimeInterval(660, 720));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void sameSlotInDifferentRoomsAreBothConfirmed() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        BookingResult r = svc.book(roomB, bob, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);
    }

    @Test
    void listBookingsReturnsConfirmedBookings() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(660, 720));
        assertEquals(2, svc.listBookings(roomA).size());
    }
    @Test
    void bookingsForRoomReturnsOnlyThatRoomsBookings() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);

        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomB, bob, new TimeInterval(600, 660));

        // 断言每个房间只有自己的预订
        assertEquals(1, store.bookingsForRoom(roomA).size());
        assertEquals(1, store.bookingsForRoom(roomB).size());
    }

    // S1: 取消已确认的预订会释放该时段
    @Test
    void cancelBookingRemovesConfirmedBooking() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        String bookingId = ((BookingResult.Confirmed) r).booking().id();

        svc.cancelBooking(bookingId);

        assertEquals(0, svc.listBookings(roomA).size());
    }

    // S2: 取消不存在的预订 id 时什么都不做
    @Test
    void cancelBookingWithUnknownIdDoesNothing() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(600, 660));

        svc.cancelBooking("no-such-id");

        assertEquals(1, svc.listBookings(roomA).size());
    }

    // S3: 取消后，最早等待且不冲突的候补者被晋升为确认预订
    @Test
    void cancelBookingPromotesEarliestFittingWaiter() {
        BookingService svc = newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        String bookingId = ((BookingResult.Confirmed) r).booking().id();
        svc.book(roomA, bob, new TimeInterval(630, 700)); // 与 Alice 冲突，进候补

        svc.cancelBooking(bookingId);

        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(bob, svc.listBookings(roomA).get(0).user());
    }

    // S5: 最早的候补者仍与其他预订冲突时被跳过，晋升下一个可行者
    @Test
    void cancelBookingSkipsConflictingWaiter() {
        BookingService svc = newService();
        User carol = new User("u3", "Carol");
        User dave = new User("u4", "Dave");
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        String bookingId = ((BookingResult.Confirmed) r).booking().id();
        svc.book(roomA, bob, new TimeInterval(650, 760));  // w1，与 Alice 冲突
        svc.book(roomA, carol, new TimeInterval(700, 760)); // 确认
        svc.book(roomA, dave, new TimeInterval(620, 680)); // w2，与 Alice 冲突

        svc.cancelBooking(bookingId);

        // w1 (Bob, 650-760) 仍与 Carol 冲突，被跳过；w2 (Dave) 被晋升
        assertEquals(2, svc.listBookings(roomA).size());
        assertEquals(dave, svc.listBookings(roomA).get(1).user());
    }
}
