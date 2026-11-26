package br.com.vanesssadb.travel_expense_api.trip;

import br.com.vanesssadb.travel_expense_api.shared.exception.BusinessRuleException;
import br.com.vanesssadb.travel_expense_api.shared.exception.ResourceNotFoundException;
import br.com.vanesssadb.travel_expense_api.trip.dto.TripRequest;
import br.com.vanesssadb.travel_expense_api.trip.dto.TripResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional
    public TripResponse create(TripRequest request) {
        String currency = validateAndNormalize(request);
        Trip trip = new Trip(request.name(), request.destination(), currency,
                request.budget(), request.startDate(), request.endDate());
        return TripResponse.from(tripRepository.save(trip));
    }

    @Transactional(readOnly = true)
    public List<TripResponse> findAll() {
        return tripRepository.findAll().stream().map(TripResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse findById(Long id) {
        return TripResponse.from(getTripOrThrow(id));
    }

    @Transactional
    public TripResponse update(Long id, TripRequest request) {
        Trip trip = getTripOrThrow(id);
        String currency = validateAndNormalize(request);
        trip.setName(request.name());
        trip.setDestination(request.destination());
        trip.setBaseCurrency(currency);
        trip.setBudget(request.budget());
        trip.setStartDate(request.startDate());
        trip.setEndDate(request.endDate());
        return TripResponse.from(trip);
    }

    @Transactional
    public void delete(Long id) {
        tripRepository.delete(getTripOrThrow(id));
    }

    private Trip getTripOrThrow(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem não encontrada: id=" + id));
    }

    /** Aplica RN01, RN02 e RN04; retorna a moeda base normalizada. */
    private String validateAndNormalize(TripRequest request) {
        // RN01
        if (request.budget() == null || request.budget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O orçamento deve ser maior que zero.");
        }
        // RN02
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("A data final não pode ser anterior à data inicial.");
        }
        // RN04
        return normalizeCurrency(request.baseCurrency());
    }

    private String normalizeCurrency(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Moeda inválida (ISO 4217): " + code);
        }
    }
}
