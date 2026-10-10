package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                int position = store.waitlistForRoom(room).size() + 1;
                int seq = nextWaitlistSeq++;
                store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
                return new BookingResult.Waitlisted(position);
            }
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }

    /**
     * Cancels the confirmed booking with {@code bookingId}, freeing its slot. If
     * no booking has that id, does nothing. After removal, promotes at most one
     * waitlist entry for that room: the earliest-waiting user (by {@code seq})
     * whose interval does not overlap any remaining confirmed booking.
     */
    public void cancelBooking(String bookingId) {
        Optional<Booking> booking = store.findBooking(bookingId);
        if (booking.isEmpty()) {
            return;
        }
        Room room = booking.get().room();
        store.removeBooking(bookingId);

        List<WaitlistEntry> waiters = new ArrayList<>(store.waitlistForRoom(room));
        waiters.sort(Comparator.comparingInt(WaitlistEntry::seq));
        for (WaitlistEntry entry : waiters) {
            boolean conflicts = false;
            for (Booking existing : store.bookingsForRoom(room)) {
                if (existing.interval().overlaps(entry.interval())) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) {
                continue;
            }
            Booking promoted =
                    new Booking("b" + nextBookingSeq++, room, entry.user(), entry.interval());
            store.addBooking(promoted);
            store.removeWaitlistEntry(entry.id());
            return;
        }
    }
}
