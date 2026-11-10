package ch.bzz.controller;

import ch.bzz.generated.api.AccountApi;
import ch.bzz.generated.model.Account;
import ch.bzz.generated.model.AccountUpdate;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.model.Project;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
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
public class AccountApiController implements AccountApi {

    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    public AccountApiController(
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            JwtUtil jwtUtil,
            HttpServletRequest request) {
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<Account>> getAccounts() {
        String projectName = getProjectName();
        List<Account> accounts = accountRepository.findByProject_ProjectName(projectName)
                .stream()
                .map(account -> new Account(account.getAccountNumber(), account.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> updateAccounts(UpdateAccountsRequest updateAccountsRequest) {
        String projectName = getProjectName();
        Project project = projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Unknown project"));

        if (updateAccountsRequest == null || updateAccountsRequest.getAccounts() == null) {
            return ResponseEntity.ok().build();
        }

        for (AccountUpdate update : updateAccountsRequest.getAccounts()) {
            if (update == null || update.getNumber() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Account number is required");
            }

            var existing = accountRepository
                    .findByAccountNumberAndProject_ProjectName(update.getNumber(), projectName);
            JsonNullable<String> name = update.getName();

            if (name != null && name.isPresent() && name.orElse(null) == null) {
                existing.ifPresent(accountRepository::delete);
            } else if (name != null && name.isPresent()) {
                ch.bzz.model.Account account = existing.orElseGet(() -> {
                    ch.bzz.model.Account newAccount = new ch.bzz.model.Account();
                    newAccount.setAccountNumber(update.getNumber());
                    newAccount.setProject(project);
                    return newAccount;
                });
                account.setName(name.get());
                accountRepository.save(account);
            } else if (existing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Name is required for a new account");
            }
        }

        return ResponseEntity.ok().build();
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
