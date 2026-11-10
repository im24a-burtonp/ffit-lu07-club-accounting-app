package ch.bzz.controller;

import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.Booking;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.model.Account;
import ch.bzz.model.Project;
import ch.bzz.repository.BookingRepository;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingApiController implements BookingApi {

    private final BookingRepository bookingRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public BookingApiController(
            BookingRepository bookingRepository,
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            JwtUtil jwtUtil,
            HttpServletRequest request) {
        this.bookingRepository = bookingRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Booking>> getBookings() {
        String projectName = getProjectName();
        List<Booking> bookings = bookingRepository.findByProject_ProjectName(projectName)
                .stream()
                .map(booking -> new Booking(
                        booking.getId(),
                        booking.getDate(),
                        booking.getText(),
                        booking.getDebitAccount().getAccountNumber(),
                        booking.getCreditAccount().getAccountNumber(),
                        booking.getAmount().floatValue()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(bookings);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> updateBookings(UpdateBookingsRequest updateBookingsRequest) {
        String projectName = getProjectName();
        Project project = projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Unknown project"));

        if (updateBookingsRequest == null || updateBookingsRequest.getEntries() == null) {
            return ResponseEntity.ok().build();
        }

        for (BookingUpdate update : updateBookingsRequest.getEntries()) {
            if (update == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Booking entry must not be null");
            }

            Optional<ch.bzz.model.Booking> existing = update.getId() == null
                    ? Optional.empty()
                    : bookingRepository.findByIdAndProject_ProjectName(
                            update.getId(), projectName);

            if (update.getId() != null && existing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Booking does not belong to the project");
            }

            if (update.getId() != null && !hasUpdateValue(update)) {
                bookingRepository.delete(existing.get());
                continue;
            }

            ch.bzz.model.Booking booking = existing.orElseGet(() -> {
                ch.bzz.model.Booking newBooking = new ch.bzz.model.Booking();
                newBooking.setProject(project);
                return newBooking;
            });

            if (update.getDate() == null || !update.getDate().isPresent()
                    || update.getDate().orElse(null) == null
                    || update.getText() == null || !update.getText().isPresent()
                    || update.getText().orElse(null) == null
                    || update.getDebit() == null || !update.getDebit().isPresent()
                    || update.getDebit().orElse(null) == null
                    || update.getCredit() == null || !update.getCredit().isPresent()
                    || update.getCredit().orElse(null) == null
                    || update.getAmount() == null || !update.getAmount().isPresent()
                    || update.getAmount().orElse(null) == null) {
                if (update.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "All fields are required for a new booking");
                }
            }

            if (isPresent(update.getDate())) {
                booking.setDate(update.getDate().get());
            }
            if (isPresent(update.getText())) {
                booking.setText(update.getText().get());
            }
            if (isPresent(update.getDebit())) {
                booking.setDebitAccount(findAccount(update.getDebit().get(), projectName));
            }
            if (isPresent(update.getCredit())) {
                booking.setCreditAccount(findAccount(update.getCredit().get(), projectName));
            }
            if (isPresent(update.getAmount())) {
                booking.setAmount(BigDecimal.valueOf(update.getAmount().get()));
            }
            if (booking.getId() == 0) {
                booking.setBookingNumber(0);
            }
            bookingRepository.save(booking);
        }

        return ResponseEntity.ok().build();
    }

    private boolean hasUpdateValue(BookingUpdate update) {
        return isPresent(update.getDate()) || isPresent(update.getText())
                || isPresent(update.getDebit()) || isPresent(update.getCredit())
                || isPresent(update.getAmount());
    }

    private <T> boolean isPresent(JsonNullable<T> value) {
        return value != null && value.isPresent() && value.orElse(null) != null;
    }

    private Account findAccount(Integer number, String projectName) {
        return accountRepository.findByAccountNumberAndProject_ProjectName(number, projectName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Account does not belong to the project"));
    }

    private String getProjectName() {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing Bearer token");
        }

        try {
            return jwtUtil.extractSubject(authorization.substring("Bearer ".length()));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid Bearer token", exception);
        }
    }
}
