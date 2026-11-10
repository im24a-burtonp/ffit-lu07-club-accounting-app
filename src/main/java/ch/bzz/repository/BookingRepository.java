package ch.bzz.repository;

import ch.bzz.model.Booking;
import ch.bzz.model.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findByProject(Project project);

    List<Booking> findByProject_ProjectName(String projectName);

    java.util.Optional<Booking> findByIdAndProject_ProjectName(int id, String projectName);
}
