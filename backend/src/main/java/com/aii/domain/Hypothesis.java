package com.aii.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "hypotheses")
@Getter
@Setter
@NoArgsConstructor
public class Hypothesis {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    private double confidence; // 0.0 - 1.0

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @OneToMany(mappedBy = "hypothesis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Evidence> evidence = new ArrayList<>();

    public enum Status {
        ACTIVE, DISCARDED, CONFIRMED
    }
}
