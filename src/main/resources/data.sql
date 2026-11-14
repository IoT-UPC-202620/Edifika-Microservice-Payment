-- Datos de prueba locales (solo corre con SEED_MODE=always). Idempotente.
-- unit_id = unidades de Residential (1..5); user_id = usuarios de IAM. La unidad 4 (Maria Rojas) queda morosa.

INSERT INTO debts (id, created_at, updated_at, amount, currency, description, due_date, status, unit_id) VALUES
  (1, now(), now(), 350.00, 'PEN', 'Mantenimiento octubre 2026',     '2026-10-31', 'PAID',    1),
  (2, now(), now(), 350.00, 'PEN', 'Mantenimiento noviembre 2026',   '2026-11-30', 'PENDING', 1),
  (3, now(), now(), 320.00, 'PEN', 'Mantenimiento octubre 2026',     '2026-10-31', 'PAID',    2),
  (4, now(), now(), 320.00, 'PEN', 'Mantenimiento noviembre 2026',   '2026-11-30', 'PENDING', 2),
  (5, now(), now(), 400.00, 'PEN', 'Mantenimiento octubre 2026',     '2026-10-31', 'PAID',    3),
  (6, now(), now(), 400.00, 'PEN', 'Mantenimiento noviembre 2026',   '2026-11-30', 'PENDING', 3),
  (7, now(), now(), 480.00, 'PEN', 'Mantenimiento septiembre 2026',  '2026-09-30', 'OVERDUE', 4),
  (8, now(), now(), 480.00, 'PEN', 'Mantenimiento octubre 2026',     '2026-10-31', 'OVERDUE', 4),
  (9, now(), now(), 150.00, 'PEN', 'Cuota extraordinaria ascensor',  '2026-12-15', 'PENDING', 4)
ON CONFLICT DO NOTHING;

INSERT INTO payments (id, created_at, updated_at, amount, currency, debt_id, decline_reason, idempotency_key, payment_date, payment_method, status, user_id) VALUES
  (1, now(), now(), 350.00, 'PEN', 1, NULL,                'seed-pay-0001', '2026-10-25 10:15:00', 'CULQI',    'CONFIRMED', 2),
  (2, now(), now(), 320.00, 'PEN', 3, NULL,                'seed-pay-0002', '2026-10-28 18:40:00', 'TRANSFER', 'CONFIRMED', 3),
  (3, now(), now(), 400.00, 'PEN', 5, NULL,                'seed-pay-0003', '2026-10-30 09:05:00', 'MANUAL',   'CONFIRMED', 4),
  (4, now(), now(), 480.00, 'PEN', 7, 'INSUFFICIENT_FUNDS', 'seed-pay-0004', '2026-10-02 21:30:00', 'CULQI',    'REJECTED',  5)
ON CONFLICT DO NOTHING;

INSERT INTO payment_transactions (id, created_at, updated_at, card_brand, card_last_four, payment_id, provider, provider_transaction_id, response_message, status) VALUES
  (1, now(), now(), 'Visa',       '1111', 1, 'CULQI', 'chr_test_seed_0001', 'Venta exitosa',        'SUCCESS'),
  (2, now(), now(), 'Mastercard', '4444', 4, 'CULQI', 'chr_test_seed_0004', 'Fondos insuficientes', 'FAILED')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('debts', 'id'), (SELECT MAX(id) FROM debts));
SELECT setval(pg_get_serial_sequence('payments', 'id'), (SELECT MAX(id) FROM payments));
SELECT setval(pg_get_serial_sequence('payment_transactions', 'id'), (SELECT MAX(id) FROM payment_transactions));
