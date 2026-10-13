package com.hotel.Hotel.service;

import com.hotel.Hotel.domain.*;
import com.hotel.Hotel.dto.request.CrearReservaRequest;
import com.hotel.Hotel.dto.response.ReservaResponse;
import com.hotel.Hotel.exception.RecursoNoEncontradoException;
import com.hotel.Hotel.exception.ReglaNegocioException;
import com.hotel.Hotel.mapper.ReservaMapper;
import com.hotel.Hotel.repository.ClienteRepository;
import com.hotel.Hotel.repository.HabitacionRepository;
import com.hotel.Hotel.repository.ReservaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private HabitacionRepository habitacionRepository;

    @Mock
    private ReservaMapper reservaMapper;

    @InjectMocks
    private ReservaService reservaService;

    @Nested
    @DisplayName("Operaciones de Creación")
    class CreacionTests {

        @Test
        @DisplayName("Debe crear una reserva pendiente cuando cliente y habitación existen")
        void debeCrearReservaPendienteCuandoDatosSonValidos() {
            UUID clienteId = UUID.randomUUID();
            UUID habitacionId = UUID.randomUUID();
            LocalDateTime fechaInicio = LocalDateTime.now().plusDays(2);
            LocalDateTime fechaFin = fechaInicio.plusDays(3);
            CrearReservaRequest request = new CrearReservaRequest(clienteId, habitacionId, fechaInicio, fechaFin);
            Cliente cliente = new Cliente("Laura Pérez", "laura@correo.com");
            Habitacion habitacion = new HabitacionEstandar("301", 2, 150.0, 2);
            ReservaResponse responseEsperado = new ReservaResponse(
                    UUID.randomUUID(),
                    "Laura Pérez",
                    "301",
                    fechaInicio,
                    fechaFin,
                    450.0,
                    "PENDIENTE");

            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
            when(habitacionRepository.findById(habitacionId)).thenReturn(Optional.of(habitacion));
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseEsperado);

            ReservaResponse resultado = reservaService.crear(request);

            assertNotNull(resultado);
            assertEquals("PENDIENTE", resultado.estado());
            assertEquals(450.0, resultado.costoTotal());
            verify(clienteRepository).findById(clienteId);
            verify(habitacionRepository).findById(habitacionId);
            verify(reservaRepository).save(any(Reserva.class));
            verify(reservaMapper).toResponse(any(Reserva.class));
        }

        @Test
        @DisplayName("Debe rechazar una reserva cuando la fecha final es anterior a la inicial")
        void debeRechazarReservaConRangoDeFechasInvalido() {
            UUID clienteId = UUID.randomUUID();
            UUID habitacionId = UUID.randomUUID();
            LocalDateTime fechaInicio = LocalDateTime.now().plusDays(5);
            CrearReservaRequest request = new CrearReservaRequest(
                    clienteId,
                    habitacionId,
                    fechaInicio,
                    fechaInicio.minusDays(1));

            assertThrows(ReglaNegocioException.class, () -> reservaService.crear(request));

            verifyNoInteractions(clienteRepository, habitacionRepository, reservaRepository, reservaMapper);
        }

        @Test
        @DisplayName("Debe rechazar una reserva cuando el cliente no existe")
        void debeRechazarReservaCuandoClienteNoExiste() {
            UUID clienteId = UUID.randomUUID();
            UUID habitacionId = UUID.randomUUID();
            LocalDateTime fechaInicio = LocalDateTime.now().plusDays(1);
            CrearReservaRequest request = new CrearReservaRequest(
                    clienteId,
                    habitacionId,
                    fechaInicio,
                    fechaInicio.plusDays(2));

            when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

            assertThrows(RecursoNoEncontradoException.class, () -> reservaService.crear(request));

            verify(clienteRepository).findById(clienteId);
            verifyNoInteractions(habitacionRepository, reservaRepository, reservaMapper);
        }

        @Test
        @DisplayName("Debe rechazar una reserva cuando el cliente está inactivo")
        void debeRechazarReservaCuandoClienteEstaInactivo() {
            UUID clienteId = UUID.randomUUID();
            UUID habitacionId = UUID.randomUUID();
            LocalDateTime fechaInicio = LocalDateTime.now().plusDays(1);
            CrearReservaRequest request = new CrearReservaRequest(
                    clienteId,
                    habitacionId,
                    fechaInicio,
                    fechaInicio.plusDays(2));
            Cliente cliente = new Cliente("Mario Díaz", "mario@correo.com");
            cliente.registrarPenalizacion();
            cliente.registrarPenalizacion();
            cliente.registrarPenalizacion();
            Habitacion habitacion = new HabitacionEstandar("302", 2, 180.0, 1);

            when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
            when(habitacionRepository.findById(habitacionId)).thenReturn(Optional.of(habitacion));

            assertThrows(ReglaNegocioException.class, () -> reservaService.crear(request));

            verify(clienteRepository).findById(clienteId);
            verify(habitacionRepository).findById(habitacionId);
            verifyNoInteractions(reservaRepository, reservaMapper);
        }
    }

    @Nested
    @DisplayName("Operaciones de Confirmación")
    class ConfirmacionTests {

        @Test
        @DisplayName("Debe confirmar exitosamente cuando la reserva existe y está PENDIENTE")
        void debeConfirmarReservaExitosamente() {
            // Arrange
            UUID reservaId = UUID.randomUUID();
            Cliente cliente = new Cliente("Beatriz Morales", "beatriz@empresa.com");
            Habitacion habitacion = new HabitacionEstandar("201", 2, 200.0, 1);

            RangoFechas fechas = new RangoFechas(
                    LocalDateTime.now().plusDays(1),
                    LocalDateTime.now().plusDays(3));
            Reserva reservaExistente = new Reserva(cliente, habitacion, fechas);

            ReservaResponse responseEsperado = new ReservaResponse(
                    reservaId,
                    "Beatriz Morales",
                    "201",
                    fechas.fechaInicio(),
                    fechas.fechaFin(),
                    400.0,
                    "CONFIRMADA");

            // Stubs
            when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reservaExistente));
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseEsperado);

            // Act
            ReservaResponse resultado = reservaService.confirmar(reservaId);

            // Assert
            assertNotNull(resultado);
            assertEquals("CONFIRMADA", resultado.estado());
            assertEquals(EstadoReserva.CONFIRMADA, reservaExistente.getEstado());

            // Verificación de comportamiento (Mocks)
            verify(reservaRepository, times(1)).findById(reservaId);
            verify(reservaRepository, times(1)).save(reservaExistente);
            verify(reservaMapper, times(1)).toResponse(reservaExistente);
        }

    }

    @Nested
    @DisplayName("Operaciones de Cancelación")
    class CancelacionTests {

        @Test
        @DisplayName("Debe cancelar una reserva existente y liberar la habitación")
        void debeCancelarReservaExistenteYLiberarHabitacion() {
            UUID reservaId = UUID.randomUUID();
            Cliente cliente = new Cliente("Andrea Ruiz", "andrea@correo.com");
            Habitacion habitacion = new HabitacionEstandar("401", 2, 220.0, 1);
            RangoFechas fechas = new RangoFechas(
                    LocalDateTime.now().plusDays(2),
                    LocalDateTime.now().plusDays(4));
            Reserva reservaExistente = new Reserva(cliente, habitacion, fechas);
            reservaExistente.confirmar();
            ReservaResponse responseEsperado = new ReservaResponse(
                    reservaId,
                    "Andrea Ruiz",
                    "401",
                    fechas.fechaInicio(),
                    fechas.fechaFin(),
                    440.0,
                    "CANCELADA");

            when(reservaRepository.findById(reservaId)).thenReturn(Optional.of(reservaExistente));
            when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(reservaMapper.toResponse(any(Reserva.class))).thenReturn(responseEsperado);

            ReservaResponse resultado = reservaService.cancelar(reservaId);

            assertNotNull(resultado);
            assertEquals("CANCELADA", resultado.estado());
            assertEquals(EstadoReserva.CANCELADA, reservaExistente.getEstado());
            assertEquals(EstadoHabitacion.DISPONIBLE, habitacion.getEstado());
            verify(reservaRepository).findById(reservaId);
            verify(reservaRepository).save(reservaExistente);
            verify(reservaMapper).toResponse(reservaExistente);
        }
    }
}
