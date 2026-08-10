# Clonetube workers

La imagen ejecuta dos consumidores independientes. Deben usar colas SQS
separadas para que una transcodificación larga no bloquee los correos.

## Correo

```bash
python main.py
```

Usa `QUEUE_URL` y las variables `SMTP_*`.

## Vídeo

```bash
python video_worker.py
```

Usa `VIDEO_QUEUE_URL`, `AWS_REGION` y `AWS_BUCKET_NAME`. En AWS EC2 no deben
configurarse claves estáticas: boto3 obtiene las credenciales del instance
profile. `S3_ENDPOINT_URL` debe quedar vacío para usar S3 de AWS.

El worker acepta como máximo 3840x2160 a 30 fps, 1080p a 60 fps, 720p a
60 fps y 480p/360p/120p a 30 fps. Conserva el original y crea únicamente
variantes inferiores bajo estas claves:

```text
videos/<uuid>/1080p.mp4
videos/<uuid>/720p.mp4
videos/<uuid>/480p.mp4
videos/<uuid>/360p.mp4
videos/<uuid>/120p.mp4
```

Cada salida es MP4 con H.264, AAC, `yuv420p` y `faststart`. Los metadatos S3
permiten reintentar un trabajo sin volver a codificar variantes terminadas.

La cola de vídeo debe tener una DLQ, `maxReceiveCount` de 3 y un visibility
timeout inicial de al menos 900 segundos. El worker amplía esa visibilidad
cada 60 segundos mientras FFmpeg continúa activo.

Permisos mínimos del rol EC2:

- SQS: `ReceiveMessage`, `DeleteMessage`, `ChangeMessageVisibility` y `GetQueueAttributes`.
- S3: `GetObject`, `PutObject` y `AbortMultipartUpload` para `videos/*`.

Ejecutar las pruebas:

```bash
python -m unittest discover -s tests
```
