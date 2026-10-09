package com.edifika.payment.payment.interfaces.rest;

import com.edifika.payment.payment.domain.exceptions.DebtNotFoundException;
import com.edifika.payment.payment.domain.exceptions.PaymentNotFoundException;
import com.edifika.payment.payment.domain.model.aggregates.Payment;
import com.edifika.payment.payment.domain.model.queries.*;
import com.edifika.payment.payment.domain.services.PaymentCommandService;
import com.edifika.payment.payment.domain.services.PaymentQueryService;
import com.edifika.payment.payment.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.edifika.payment.payment.interfaces.rest.resources.*;
import com.edifika.payment.payment.interfaces.rest.transform.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST del microservicio Payment.
 * <p>
 * Recibe las solicitudes de deudas y pagos, delega en los servicios de aplicación y
 * transforma entre Resources (DTOs) y Commands/Queries del dominio.
 * La identidad del usuario se toma siempre del JWT ({@link AuthenticatedUser}):
 * <ul>
 *     <li>Solo ADMIN puede crear deudas, revisar pagos pendientes y confirmar/rechazar pagos.</li>
 *     <li>Un residente solo puede ver sus propios pagos; ADMIN puede ver los de cualquiera.</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/v1/payments", produces = "application/json")
@Tag(name = "Payments", description = "Gestión de deudas y pagos")
public class PaymentController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    public PaymentController(PaymentCommandService paymentCommandService,
                             PaymentQueryService paymentQueryService) {
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
    }

    // ---------- Deudas ----------

    /**
     * Registra una nueva deuda asociada a una unidad residencial.
     * TS07 - Escenario 1: retorna 201 con el registro creado.
     */
    @PostMapping("/debts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar una deuda (ADMIN)")
    public ResponseEntity<DebtResource> createDebt(@RequestBody CreateDebtResource resource) {
        var command = CreateDebtCommandFromResourceAssembler.toCommandFromResource(resource);
        return paymentCommandService.createDebt(command)
                .map(DebtResourceFromEntityAssembler::toResourceFromEntity)
                .map(debtResource -> new ResponseEntity<>(debtResource, HttpStatus.CREATED))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /**
     * Consulta una deuda por su id.
     */
    @GetMapping("/debts/{debtId}")
    @Operation(summary = "Obtener una deuda por id")
    public ResponseEntity<DebtResource> getDebtById(@PathVariable Long debtId) {
        var debt = paymentQueryService.getDebtById(new GetDebtByIdQuery(debtId))
                .orElseThrow(() -> new DebtNotFoundException(debtId));
        return ResponseEntity.ok(DebtResourceFromEntityAssembler.toResourceFromEntity(debt));
    }

    /**
     * Consulta las deudas asociadas a una unidad residencial (US21).
     */
    @GetMapping("/debts/unit/{unitId}")
    @Operation(summary = "Listar las deudas de una unidad")
    public ResponseEntity<List<DebtResource>> getDebtsByUnit(@PathVariable Long unitId) {
        var debts = paymentQueryService.getDebtsByUnit(new GetDebtsByUnitQuery(unitId));
        return ResponseEntity.ok(debts.stream().map(DebtResourceFromEntityAssembler::toResourceFromEntity).toList());
    }

    // ---------- Pagos ----------

    /**
     * Registra un intento de pago del usuario autenticado por el monto de la deuda.
     * Si el método es CULQI se cobra en línea (TS07 - Escenario 2 éxito / Escenario 3 fallo
     * controlado); en otro caso queda en REGISTERED a la espera de revisión del administrador.
     * Un reintento con el mismo Idempotency-Key devuelve el pago existente (200) sin volver a cobrar.
     */
    @PostMapping(consumes = "application/json")
    @Operation(
            summary = "Pagar una deuda (usuario autenticado)",
            description = "Cobra el monto de la deuda registrado en el sistema (el cliente no envía monto). "
                    + "Con CULQI el cobro es inmediato; con VOUCHER, TRANSFER o MANUAL el pago queda REGISTERED "
                    + "hasta que un ADMIN lo resuelva. Un rechazo de tarjeta responde 201 con status REJECTED, "
                    + "declineReason y declineMessage.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago procesado: CONFIRMED, REJECTED (con motivo) o REGISTERED",
                    content = @Content(schema = @Schema(implementation = PaymentResource.class))),
            @ApiResponse(responseCode = "200", description = "Reintento con un Idempotency-Key ya usado: se devuelve el pago existente sin volver a cobrar",
                    content = @Content(schema = @Schema(implementation = PaymentResource.class))),
            @ApiResponse(responseCode = "400", description = "Falta el header Idempotency-Key, el token de tarjeta, el correo o el método de pago es inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente o inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "La deuda no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "409", description = "La deuda ya está pagada, tiene un pago en curso o el Idempotency-Key se usó para otro pago",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "502", description = "La pasarela no respondió a tiempo: el pago queda PENDING_VERIFICATION y no debe reintentarse",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class)))
    })
    public ResponseEntity<PaymentResource> registerPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(in = ParameterIn.HEADER, name = IDEMPOTENCY_KEY_HEADER, required = true,
                    description = "Identificador único del intento de pago (ej. un UUID generado por la app). "
                            + "Reutilícelo solo al reintentar el mismo pago.",
                    example = "3f6c1b8e-7a2d-4c1e-9b0a-2d5e8f1a6c34")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody RegisterPaymentResource resource) {
        var command = RegisterPaymentCommandFromResourceAssembler.toCommandFromResource(
                resource, idempotencyKey, user.requireUserId(), user.email());
        return paymentCommandService.registerPayment(command)
                .map(result -> ResponseEntity
                        .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                        .body(PaymentResourceFromEntityAssembler.toResourceFromEntity(result.payment())))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /**
     * Consulta un pago por su id. Solo el dueño del pago o un ADMIN pueden verlo.
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Obtener un pago por id")
    public ResponseEntity<PaymentResource> getPaymentById(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable Long paymentId) {
        Payment payment = paymentQueryService.getPaymentById(new GetPaymentByIdQuery(paymentId))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        ensureCanAccessDataOf(user, payment.getUserId());
        return ResponseEntity.ok(PaymentResourceFromEntityAssembler.toResourceFromEntity(payment));
    }

    /**
     * Lista los pagos manuales pendientes de revisión, del más antiguo al más reciente (US22/US23).
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar pagos pendientes de revisión (ADMIN)")
    public ResponseEntity<List<PaymentResource>> getPendingReviewPayments() {
        var payments = paymentQueryService.getPendingReviewPayments(new GetPendingReviewPaymentsQuery());
        return ResponseEntity.ok(toResources(payments));
    }

    /**
     * Aprueba manualmente un pago REGISTERED o PENDING_VERIFICATION y marca la deuda como pagada
     * (ej. validación de voucher por el administrador - US22/US23, o resolución de un timeout).
     * Para rechazar se usa {@link #rejectPayment(Long, RejectPaymentResource)}.
     */
    @PutMapping("/{paymentId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aprobar un pago pendiente (ADMIN)",
            description = "Confirma un pago REGISTERED (voucher, transferencia) o PENDING_VERIFICATION "
                    + "(cargo verificado en Culqi) y marca la deuda como PAID. Para rechazar use PUT /{paymentId}/reject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago confirmado; la deuda queda PAID",
                    content = @Content(schema = @Schema(implementation = PaymentResource.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "404", description = "El pago no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "409", description = "El pago ya fue confirmado o rechazado",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class)))
    })
    public ResponseEntity<PaymentResource> confirmPayment(@PathVariable Long paymentId,
                                                          @RequestBody(required = false) ConfirmPaymentResource resource) {
        var command = ConfirmPaymentCommandFromResourceAssembler.toCommandFromResource(paymentId, resource);
        return toResponse(paymentCommandService.confirmPayment(command));
    }

    /**
     * Rechaza manualmente un pago REGISTERED o PENDING_VERIFICATION. La deuda queda libre otra vez.
     */
    @PutMapping("/{paymentId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rechazar un pago pendiente (ADMIN)",
            description = "Usado, por ejemplo, cuando se verifica en Culqi que un pago PENDING_VERIFICATION no se cobró.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago rechazado; la deuda queda libre para un nuevo intento",
                    content = @Content(schema = @Schema(implementation = PaymentResource.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "404", description = "El pago no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class))),
            @ApiResponse(responseCode = "409", description = "El pago ya fue confirmado o rechazado",
                    content = @Content(schema = @Schema(implementation = ErrorResource.class)))
    })
    public ResponseEntity<PaymentResource> rejectPayment(@PathVariable Long paymentId,
                                                         @RequestBody(required = false) RejectPaymentResource resource) {
        var command = ConfirmPaymentCommandFromResourceAssembler.toRejectCommandFromResource(paymentId, resource);
        return toResponse(paymentCommandService.confirmPayment(command));
    }

    /**
     * Consulta el historial de pagos de un residente (US28).
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Historial de pagos de un usuario")
    public ResponseEntity<List<PaymentResource>> getPaymentsByUser(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PathVariable Long userId) {
        ensureCanAccessDataOf(user, userId);
        var payments = paymentQueryService.getPaymentsByUser(new GetPaymentsByUserQuery(userId));
        return ResponseEntity.ok(toResources(payments));
    }

    /**
     * Consulta el historial de pagos de un residente filtrado por año (US28 - E2).
     */
    @GetMapping("/user/{userId}/year/{year}")
    @Operation(summary = "Historial de pagos de un usuario por año")
    public ResponseEntity<List<PaymentResource>> getPaymentHistoryByYear(@AuthenticationPrincipal AuthenticatedUser user,
                                                                         @PathVariable Long userId,
                                                                         @PathVariable Integer year) {
        ensureCanAccessDataOf(user, userId);
        var payments = paymentQueryService.getPaymentHistoryByYear(new GetPaymentHistoryByYearQuery(userId, year));
        return ResponseEntity.ok(toResources(payments));
    }

    private static void ensureCanAccessDataOf(AuthenticatedUser user, Long ownerUserId) {
        if (!user.canAccessDataOf(ownerUserId)) {
            throw new AccessDeniedException("No puede acceder a los pagos de otro usuario");
        }
    }

    private static ResponseEntity<PaymentResource> toResponse(Optional<Payment> payment) {
        return payment
                .map(PaymentResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    private static List<PaymentResource> toResources(List<Payment> payments) {
        return payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
    }
}
