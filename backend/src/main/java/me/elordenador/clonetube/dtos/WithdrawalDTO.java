package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalDTO {
    private Long id;
    private Integer points;
    private BigDecimal eurValue;
    private String status;
    private String provider;
    private String destination;
    private String providerPayoutId;
    private Instant createdAt;
}
