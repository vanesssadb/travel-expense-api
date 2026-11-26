package br.com.vanesssadb.travel_expense_api.trip;

import br.com.vanesssadb.travel_expense_api.shared.exception.BusinessRuleException;
import br.com.vanesssadb.travel_expense_api.shared.exception.ResourceNotFoundException;
import br.com.vanesssadb.travel_expense_api.trip.dto.TripRequest;
import br.com.vanesssadb.travel_expense_api.trip.dto.TripResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    private static final Long EXISTING_ID = 1L;
    private static final Long MISSING_ID = 99L;
    private static final LocalDate START = LocalDate.of(2026, 12, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 10);

    @Mock
    private TripRepository tripRepository;

    @InjectMocks
    private TripService tripService;

    private static TripRequest request(String currency, BigDecimal budget, LocalDate start, LocalDate end) {
        return new TripRequest("Férias", "Lisboa", currency, budget, start, end);
    }

    private static TripRequest validRequest() {
        return request("eur", new BigDecimal("5000.00"), START, END);
    }

    private static Trip existingTrip() {
        return new Trip("Antiga", "Paris", "BRL", new BigDecimal("1000.00"), START, END);
    }

    // ---------- create ----------

    @Test
    void create_deveNormalizarMoedaESalvar() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse response = tripService.create(validRequest());

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        Trip saved = captor.getValue();
        assertThat(saved.getBaseCurrency()).isEqualTo("EUR");
        assertThat(saved.getName()).isEqualTo("Férias");
        assertThat(saved.getBudget()).isEqualByComparingTo("5000.00");
        assertThat(response.baseCurrency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10.00"})
    void create_deveFalharQuandoBudgetNaoPositivo_RN01(String budget) {
        TripRequest req = request("EUR", new BigDecimal(budget), START, END);

        assertThatThrownBy(() -> tripService.create(req)).isInstanceOf(BusinessRuleException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void create_deveFalharQuandoEndDateAnteriorAStartDate_RN02() {
        TripRequest req = request("EUR", BigDecimal.TEN, END, START);

        assertThatThrownBy(() -> tripService.create(req)).isInstanceOf(BusinessRuleException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void create_deveFalharQuandoMoedaInvalida_RN04() {
        TripRequest req = request("XYZ", BigDecimal.TEN, START, END);

        assertThatThrownBy(() -> tripService.create(req)).isInstanceOf(BusinessRuleException.class);
        verify(tripRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_deveAtualizarQuandoIdExiste() {
        Trip trip = existingTrip();
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(trip));

        TripResponse response = tripService.update(EXISTING_ID, validRequest());

        assertThat(trip.getName()).isEqualTo("Férias");
        assertThat(trip.getDestination()).isEqualTo("Lisboa");
        assertThat(trip.getBaseCurrency()).isEqualTo("EUR");
        assertThat(trip.getBudget()).isEqualByComparingTo("5000.00");
        assertThat(response.baseCurrency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void update_deveFalharQuandoBudgetNaoPositivo_RN01(String budget) {
        Trip trip = existingTrip();
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(trip));
        TripRequest req = request("EUR", new BigDecimal(budget), START, END);

        assertThatThrownBy(() -> tripService.update(EXISTING_ID, req)).isInstanceOf(BusinessRuleException.class);
        assertThat(trip.getBudget()).isEqualByComparingTo("1000.00");
    }

    @Test
    void update_deveFalharQuandoEndDateAnteriorAStartDate_RN02() {
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(existingTrip()));
        TripRequest req = request("EUR", BigDecimal.TEN, END, START);

        assertThatThrownBy(() -> tripService.update(EXISTING_ID, req)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_deveFalharQuandoMoedaInvalida_RN04() {
        Trip trip = existingTrip();
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(trip));
        TripRequest req = request("XYZ", BigDecimal.TEN, START, END);

        assertThatThrownBy(() -> tripService.update(EXISTING_ID, req)).isInstanceOf(BusinessRuleException.class);
        assertThat(trip.getBaseCurrency()).isEqualTo("BRL");
    }

    @Test
    void update_deveFalharQuandoIdInexistente() {
        when(tripRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.update(MISSING_ID, validRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- findById ----------

    @Test
    void findById_deveRetornarQuandoIdExiste() {
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(existingTrip()));

        assertThat(tripService.findById(EXISTING_ID).name()).isEqualTo("Antiga");
    }

    @Test
    void findById_deveFalharQuandoIdInexistente() {
        when(tripRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.findById(MISSING_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_deveExcluirQuandoIdExiste() {
        Trip trip = existingTrip();
        when(tripRepository.findById(EXISTING_ID)).thenReturn(Optional.of(trip));

        tripService.delete(EXISTING_ID);

        verify(tripRepository).delete(trip);
    }

    @Test
    void delete_deveFalharQuandoIdInexistente() {
        when(tripRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.delete(MISSING_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tripRepository, never()).delete(any());
    }
}
