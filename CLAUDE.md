# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# TurnoYa

Reserva de turnos con seña obligatoria vía Mercado Pago. El turno solo se confirma cuando el cliente paga la seña;
mientras paga, el horario queda retenido (`turnoya.booking.hold-ttl`, 10 min) y si no paga se libera solo.

Stack: Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL (H2 en modo PostgreSQL por defecto), Flyway, Kafka (perfil `kafka`),
JUnit 5 + Mockito + MockMvc.

## Modo aprendizaje
Soy el desarrollador. Explicame el porqué de cada decisión,
dejame escribir la lógica de dominio y revisá mi código. No escribas todo vos.

## Convenciones
- Paquetes por módulo (`business`, `booking`, `payment`, `outbox`; lo transversal en `shared`).
- Records para DTOs (agrupados en `*Dtos.java` por módulo).
- ProblemDetail para errores: las excepciones de dominio se mapean a status HTTP en `shared/ApiExceptionHandler`.
- Cada feature con test.

## Comandos

No hay Maven wrapper: se usa `mvn` instalado.

```bash
mvn spring-boot:run                       # H2 en memoria + pagos simulados + negocio demo
docker compose up --build                 # app + PostgreSQL 17 + Kafka (perfiles postgres,kafka)
mvn test                                  # solo unitarios (*Test, surefire)
mvn verify                                # unitarios + integración (*IT, failsafe) — lo que corre la CI
mvn test -Dtest=ScheduleRulesTest         # un test unitario (o -Dtest=Clase#metodo)
mvn verify -Dit.test=BookingFlowIT        # un test de integración (corre también los unitarios)
```

- App web: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui/index.html
- Simular un pago en desarrollo: `POST /dev/payments/{bookingId}/approve`.
- Kafka sin compose: `mvn spring-boot:run -Dspring-boot.run.profiles=kafka` con un broker en `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).
- Mercado Pago real: perfil `mercadopago` con `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` y `PUBLIC_BASE_URL`.

## Arquitectura

**Perfiles y beans intercambiables.** Sin perfil se usan `FakePaymentGateway` + `DevPaymentController`
(`@Profile("!mercadopago")`); con `mercadopago` se activa `MercadoPagoGateway`. `postgres` solo cambia el datasource. `kafka` reemplaza `LoggingEventPublisher` por `KafkaEventPublisher`.
`turnoya.demo-data` controla `DemoDataLoader`, y `turnoya.scheduling.enabled` habilita los jobs `@Scheduled`.

**Reserva en dos pasos (`BookingService#reserve`).** Primero una transacción corta toma un lock pesimista sobre el
`Business` (`BusinessRepository#findByIdForUpdate`, `SELECT ... FOR UPDATE`), chequea solapamiento e inserta el hold.
Después, ya fuera de la transacción, crea el checkout en el gateway. Si el gateway falla, la reserva se cancela.
No metas llamadas externas dentro de la transacción con el lock tomado: serializaría todas las reservas del negocio.
Las transacciones se manejan con `TransactionTemplate` justamente para poder partirla.

**Ciclo de vida (`BookingStatus`).** `PENDING_PAYMENT` → `CONFIRMED` | `EXPIRED` | `CANCELLED`. Un pago tardío sobre
`EXPIRED` confirma si el horario sigue libre o pasa a `REFUND_REQUIRED` si otro lo tomó (`applyApprovedPayment`).
Las transiciones viven como métodos en la entidad `Booking`, no en los servicios.

**Concurrencia entre job y webhook.** `HoldExpirationJob` vence holds; `Booking` tiene `@Version`. Si el webhook de
pago y el job tocan la misma reserva, el job captura `ObjectOptimisticLockingFailureException` y gana el pago.

**Webhooks idempotentes.** `MercadoPagoWebhookController` → `MercadoPagoSignatureVerifier` (HMAC-SHA256 de `x-signature`,
comparación en tiempo constante) → `PaymentNotificationService`, que registra el webhook en `processed_webhook`
(unique constraint) en la misma transacción que la confirmación y consulta el pago a la API en vez de confiar en el body.

**Transactional outbox.** Todo cambio de estado relevante llama a `OutboxWriter#append`, que es
`Propagation.MANDATORY`: falla si no hay transacción activa, y así el evento se guarda atómicamente con el cambio.
`OutboxRelay` publica los pendientes cada 2 s con entrega al menos una vez, por lo que los consumidores deben ser
idempotentes. Con el perfil `kafka`, cada evento va al topic `turnoya.<aggregateType>` con key = `aggregateId` (orden por
reserva) y headers `eventId` (para deduplicar) y `eventType`. `publish()` espera el ack del broker con timeout: si falla,
`relayPending()` hace rollback y el evento se reintenta. Cada tipo de agregado nuevo necesita su `NewTopic` en `KafkaTopicsConfig`.
Con varias instancias publica una sola: `relayPending()` primero toma la fila de `outbox_relay_lock` con
`FOR UPDATE SKIP LOCKED` y, si otra instancia la tiene, se saltea el ciclo. No repartas eventos entre relays en paralelo
(SKIP LOCKED por evento), porque rompe el orden por reserva. `OutboxCleanupJob` borra a diario los eventos publicados
hace más de `turnoya.outbox.retention` (7 días); los pendientes nunca se borran.

**Tiempo inyectable.** Nunca usar `Instant.now()`: todo toma un `Clock` (bean en `AppConfig`). Los horarios se guardan
como `Instant` UTC y se convierten con la zona horaria del negocio (`Business#getZoneId`) para la agenda y las reglas
(`ScheduleRules`).

**Esquema.** Solo Flyway (`db/migration`, `ddl-auto: none`): los cambios de modelo requieren una migración `V{n}__*.sql`
nueva, compatible con H2 (modo PostgreSQL) y con PostgreSQL. `open-in-view` está desactivado.

**Frontend.** HTML + JS sin frameworks en `src/main/resources/static`, servido por el mismo Spring Boot. Los datos del
usuario se insertan con `textContent`, nunca como HTML.

## Tests

- Los tests de integración extienden `support/IntegrationTest`: perfil `test` (H2, sin demo data, sin scheduling),
  `MutableClock` fijado en `NOW` (lunes 5/10/2026 09:00 Córdoba) y el helper `newBusinessWithHaircut()`.
- Para probar vencimientos, adelantá `clock` y llamá a los jobs a mano (`expireDueHolds()`, `relayPending()`),
  porque el scheduling está apagado en tests.
- `KafkaOutboxRelayIT` usa `@EmbeddedKafka` (broker en memoria, sin Docker). La base H2 de tests es compartida entre
  contextos, así que no asumas que el outbox arranca vacío.
- `OutboxRelayLockIT` simula dos instancias con un `CountDownLatch`: si el relay esperara el candado en vez de saltearlo,
  falla por timeout.
- `BookingConcurrencyIT` lanza reservas simultáneas al mismo horario; si tocás el locking, tiene que seguir pasando.
