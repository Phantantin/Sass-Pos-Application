package com.tindev.payload.dto;

import com.tindev.domain.StoreStatus;
import com.tindev.modal.StoreContact;
import com.tindev.modal.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoreDto {

    private Long id;

    private String brand;

    private UserDto storeAdmin;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String description;

    private String storeType;

    private StoreStatus status;


    private StoreContact contact;

}
