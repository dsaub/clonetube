#!/usr/bin/env bash
set -euo pipefail

CERT_DIR="$(dirname "$0")/certs"

mkdir -p "$CERT_DIR"

if [ -f "$CERT_DIR/privkey.pem" ] && [ -f "$CERT_DIR/fullchain.pem" ]; then
    echo "Los certificados ya existen en $CERT_DIR. No se sobrescriben."
    exit 0
fi

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "$CERT_DIR/privkey.pem" \
    -out "$CERT_DIR/fullchain.pem" \
    -subj "/C=ES/ST=Madrid/L=Madrid/O=Clonetube/OU=Dev/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,DNS:*.localhost,IP:127.0.0.1"

chmod 600 "$CERT_DIR/privkey.pem"
chmod 644 "$CERT_DIR/fullchain.pem"

echo "Certificados autofirmados generados en $CERT_DIR"
echo "  - Privada: $CERT_DIR/privkey.pem"
echo "  - Pública: $CERT_DIR/fullchain.pem"
echo "  - Válido por 365 días"
echo ""
echo "AVISO: Son autofirmados. El navegador mostrará una advertencia."
echo "       En producción usa Let's Encrypt o un CA real."
