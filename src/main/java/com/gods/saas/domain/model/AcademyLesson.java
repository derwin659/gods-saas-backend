package com.gods.saas.domain.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="academy_lesson") @Getter @Setter
public class AcademyLesson {
 @Id @Column(length=80) private String id;
 @Column(nullable=false,columnDefinition="text") private String draftJson;
 @Column(columnDefinition="text") private String publishedJson;
 @Version private Long version;
 @Column(nullable=false) private Instant updatedAt = Instant.now();
}
