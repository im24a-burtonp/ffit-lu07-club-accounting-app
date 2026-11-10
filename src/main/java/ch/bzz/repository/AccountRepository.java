package ch.bzz.repository;

import ch.bzz.model.Account;
import ch.bzz.model.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    List<Account> findByProject(Project project);

    List<Account> findByProject_ProjectName(String projectName);

    java.util.Optional<Account> findByAccountNumberAndProject_ProjectName(
            int accountNumber, String projectName);
}
