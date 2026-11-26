package br.com.vanesssadb.travel_expense_api.trip.dto;

import br.com.vanesssadb.travel_expense_api.trip.Trip;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TripResponse(
        Long id,
        String name,
        String destination,
        String baseCurrency,
        BigDecimal budget,
        LocalDate startDate,
        LocalDate endDate
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                trip.getId(),
                trip.getName(),
                trip.getDestination(),
                trip.getBaseCurrency(),
                trip.getBudget(),
                trip.getStartDate(),
                trip.getEndDate()
        );
    }
}
