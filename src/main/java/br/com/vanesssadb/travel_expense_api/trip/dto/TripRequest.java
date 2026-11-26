package br.com.vanesssadb.travel_expense_api.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TripRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String destination,
        @NotBlank @Size(min = 3, max = 3) String baseCurrency,
        @NotNull @Positive BigDecimal budget,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
