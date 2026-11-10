package ch.bzz.controller;

import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.model.Project;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ProjectApiController implements ProjectApi {

    private final ProjectRepository projectRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public ProjectApiController(ProjectRepository projectRepository, JwtUtil jwtUtil) {
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ResponseEntity<Void> createProject(LoginRequest loginRequest) {
        if (projectRepository.existsById(loginRequest.getProjectName())) {
            log.warn("Project registration rejected because the project already exists: {}",
                    loginRequest.getProjectName());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Project project = new Project();
        project.setProjectName(loginRequest.getProjectName());
        project.setPasswordHash(encoder.encode(loginRequest.getPassword()));
        projectRepository.save(project);
        log.info("Project registered successfully: {}", loginRequest.getProjectName());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<LoginProject200Response> loginProject(LoginRequest loginRequest) {
        Project project = projectRepository.findById(loginRequest.getProjectName()).orElse(null);
        if (project == null || !encoder.matches(loginRequest.getPassword(), project.getPasswordHash())) {
            log.warn("Login rejected for project: {}", loginRequest.getProjectName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LoginProject200Response response = new LoginProject200Response()
                .accessToken(jwtUtil.generateToken(project.getProjectName()));
        log.info("Login successful for project: {}", project.getProjectName());
        return ResponseEntity.ok(response);
    }
}
