-- Candado del relay del outbox: la instancia que bloquea esta fila publica y las demás se saltean el ciclo.
-- Un solo relay activo a la vez mantiene el orden de los eventos de cada reserva.
CREATE TABLE outbox_relay_lock (
    id INT PRIMARY KEY
);

INSERT INTO outbox_relay_lock (id) VALUES (1);
