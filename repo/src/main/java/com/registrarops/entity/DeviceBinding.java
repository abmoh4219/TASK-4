package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_bindings")
@Getter
@Setter
public class DeviceBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_hash", nullable = false, length = 128)
    private String deviceHash;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt;
}
