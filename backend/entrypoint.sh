#!/bin/sh
set -e

echo "=== Ejecutando migraciones con Alembic ==="
alembic upgrade head
echo "=== Migraciones completadas ==="

exec "$@"
