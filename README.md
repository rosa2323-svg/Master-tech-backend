# Master Tech — Backend (Spring Boot)

API REST de la tienda Master Tech: catálogo, usuarios con roles, cotización de envíos, pago con Stripe, despacho de pedidos, reembolsos y dashboard de ventas.

**Stack:** Java 17 · Spring Boot 4 · Gradle · Spring Security + JWT · Spring Data JPA · PostgreSQL (Supabase) · Stripe · Supabase Storage · Google Maps (opcional)

| Servicio | Local | Render |
|---|---|---|
| [Frontend](https://github.com/rosa2323-svg/Master-tech-frontend) | http://localhost:5173 | https://herramientas01.onrender.com |
| [Backend Spring Boot (este repo)](https://github.com/rosa2323-svg/Master-tech-backend) | http://localhost:8089 | https://master-tech-backend.onrender.com |
| [Asistente IA](https://github.com/MaelXDD/Bot_Asitente.git) | http://localhost:8090/mastertech | https://bot-asitente.onrender.com/mastertech |

## Documentación
Arquitectura, endpoints, seguridad, flujo de pago, envíos, reembolsos, base de datos, despliegue y pruebas: **[wiki de este repositorio](https://github.com/rosa2323-svg/Master-tech-backend/wiki)**.

## Puesta en marcha
Requiere Java 17 y una base PostgreSQL con el esquema ya creado (Hibernate solo lo valida, no lo crea).

```bash
cp .env.example .env     # valores de ejemplo
./gradlew bootRun        # http://localhost:8089
./gradlew test           # pruebas
```
Spring Boot no lee `.env`: define las variables en IntelliJ (*Run → Edit Configurations*), expórtalas en tu terminal o configúralas en Render. Comprobación: `GET /api/health`.

## Variables de entorno
**Obligatorias**
| Variable | Descripción |
|---|---|
| `JWT_SECRET` | Secreto del JWT (mínimo 32 caracteres). El bot debe usar el mismo |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a PostgreSQL |
| `STRIPE_SECRET_KEY` | Clave secreta de Stripe (modo de prueba) |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | Supabase Storage para las imágenes (la `service_role` solo va en el servidor) |

**Opcionales**
| Variable | Por defecto | Descripción |
|---|---|---|
| `FRONTEND_URL` | `http://localhost:5173` | URL del front (CORS y regreso desde Stripe) |
| `CORS_ALLOWED_ORIGINS` | = `FRONTEND_URL` | Varios orígenes separados por coma |
| `STRIPE_PUBLISHABLE_KEY` | vacío | Clave pública para el pago incrustado del front |
| `STRIPE_WEBHOOK_SECRET` | vacío | Firma del webhook `/api/stripe/webhook` (vacío = desactivado) |
| `STRIPE_CHECKOUT_EXPIRACION_MINUTOS` | `30` | Tiempo para pagar (30 a 1440) |
| `SUPABASE_BUCKET_PRODUCTOS` | `Productos` | Bucket de imágenes |
| `GOOGLE_MAPS_SERVER_KEY` | vacío | Clave de servidor: distancia por ruta y autocompletado de direcciones |
| `PORT` | `8089` | Puerto |

**Nunca subas secretos al repositorio.**

## Roles
`USER` (cliente, por defecto) · `DESPACHADOR` (ver pedidos, cambiar su estado y resolver reembolsos) · `ADMIN` (todo, incluida la administración). El catálogo es público en solo lectura. Detalle en la wiki: *Seguridad y roles*.

## Despliegue
Render, como servicio Docker (`render.yaml` + `Dockerfile`). Las variables se definen en el panel; el webhook de Stripe apunta a `/api/stripe/webhook`. Pasos en la wiki: *Despliegue*.

## Contribuir
Issue en el tablero → rama desde `develop` (`feature/<nombre>-pr<N>-<tema>`) → commits `feat:`/`fix:`/`docs:` → PR hacia `develop` con la plantilla y `Closes #N`. Las pruebas deben pasar en GitHub Actions.
