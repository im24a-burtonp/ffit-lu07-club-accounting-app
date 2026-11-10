package ch.bzz.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Setter
@Entity
@Table(name = "projects")
public class Project {
    @Id
    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
}
