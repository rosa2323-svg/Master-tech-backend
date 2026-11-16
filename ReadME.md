# Master Tech — Backend (Spring Boot)

API REST de la tienda Master Tech: catálogo, usuarios con roles, pago con Stripe, despacho de pedidos y dashboard de ventas.

**Stack:** Java 17 · Spring Boot 4 · Gradle · Spring Security + JWT · PostgreSQL (Supabase) · Stripe

| Servicio | Local | Render |
|---|---|---|
| [Frontend](https://github.com/rosa2323-svg/Master-tech-frontend.git)| http://localhost:5173 | https://herramientas01.onrender.com |
| [Backend Spring Boot (este repo)](https://github.com/rosa2323-svg/Master-tech-backend) | http://localhost:8089 | https://master-tech-backend.onrender.com |
| [Asistente IA](https://github.com/MaelXDD/Bot_Asitente.git)| http://localhost:8090/mastertech | https://bot-asitente.onrender.com/mastertech |

## Puesta en marcha
Requiere Java 17 y una base PostgreSQL con el esquema ya creado (Hibernate solo lo valida, no lo crea).

```bash
cp .env.example .env     # define las variables en IntelliJ (Run → Edit Configurations) o en Render
./gradlew bootRun        # http://localhost:8089
./gradlew test           # pruebas unitarias
```

## Variables de entorno
| Variable | Descripción |
|---|---|
| `JWT_SECRET` | Secreto del JWT (mínimo 32 caracteres, obligatoria). El bot debe usar el mismo |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a PostgreSQL |
| `STRIPE_SECRET_KEY` | Clave secreta de Stripe (modo de prueba) |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | Supabase Storage para las imágenes (la `service_role` solo va en el servidor) |
| `FRONTEND_URL` | URL del front para CORS y Stripe (por defecto `http://localhost:5173`) |

Opcionales: `SUPABASE_BUCKET_PRODUCTOS` (por defecto `Productos`), `CORS_ALLOWED_ORIGINS` y `PORT` (por defecto `8089`). **Nunca subas secretos al repositorio.**

## Roles
`USER` (cliente, por defecto) · `DESPACHADOR` (ver pedidos y cambiar su estado) · `ADMIN` (todo, incluida la administración). El catálogo es público en solo lectura.

## Contribuir
Issue en el tablero → rama desde `develop` (`feature/<nombre>-pr<N>-<tema>`) → commits `feat:`/`fix:`/`docs:` → PR hacia `develop` con la plantilla y `Closes #N`. Las pruebas deben pasar en GitHub Actions.
