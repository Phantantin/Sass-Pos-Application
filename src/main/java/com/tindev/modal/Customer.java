package com.tindev.modal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Tên khách hàng là bắt buộc")
    @Size(max = 120, message = "Tên khách hàng không được quá 120 ký tự")
    private String fullName;
    
    @Email(message = "Email khách hàng không hợp lệ")
    @Size(max = 254, message = "Email khách hàng không được quá 254 ký tự")
    private String email;
    
    @Size(max = 30, message = "Số điện thoại không được quá 30 ký tự")
    private String phone;
    
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Store store;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
