package com.tindev.modal;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_category_store_name", columnNames = {"store_id", "name"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    private Store store;
}
