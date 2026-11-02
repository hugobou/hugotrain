# HugoTrain

App Android personal para gestionar un entrenamiento híbrido (carrera en carretera, trail, natación, fuerza y caminata) usando **Google Sheets como base de datos** y **Strava como puente** para las actividades del reloj Garmin.

No depende de ningún servidor: la app habla directamente con Google Sheets y con Strava.

## Qué hace

- **Semana**: muestra el plan de la semana (pestaña `Plan`), marca como hecha cada sesión cuando encuentra una actividad del mismo deporte ese día y resume tiempo, kilómetros y carga frente a la semana anterior (avisa si sube más de un 10 %).
- **Fuerza**: registra sesiones de gimnasio o peso corporal a partir de plantillas, con cronómetro de descanso que vibra al terminar y sugerencia de progresión (si completaste todas las series, propone subir 2,5 kg o 1-2 reps). Funciona sin conexión: las series se guardan en el móvil y se suben al recuperar red.
- **Actividades**: importa de Strava las actividades nuevas (carrera, trail, natación, caminata…) a la pestaña `Actividades`.
- **Ajustes**: conexión con Google, creación de la hoja y conexión con Strava.

## Estructura de la hoja

La app crea la hoja con estas pestañas (la fila 1 es la cabecera):

| Pestaña | Columnas |
|---|---|
| `Plan` | Fecha · Deporte · Tipo · Descripción · Duración (min) · Distancia (km) · Intensidad · Plantilla · Notas · Estado |
| `Actividades` | Strava ID · Fecha · Deporte · Nombre · Distancia (km) · Duración (min) · Ritmo (min/km) · Desnivel (m) · FC media · FC máx · RPE |
| `Fuerza` | Fecha · Sesión · Ejercicio · Serie · Reps · Peso (kg) · RPE · Notas |
| `Plantillas` | Plantilla · Ejercicio · Tipo · Series · Reps · Peso (kg) · Descanso (s) |

Deportes reconocidos en la columna Deporte: `Carrera`, `Trail`, `Natación`, `Fuerza`, `Caminata`, `Senderismo`, `Bici`, `Descanso`.
En `Plantillas`, la columna Tipo es `Gimnasio` o `Peso corporal`. En `Plan`, si una sesión de fuerza indica una Plantilla, aparece como sugerida ese día en la pestaña Fuerza.

## Puesta en marcha (una sola vez)

### 1. Google Cloud (para que la app pueda usar Sheets)

1. Entra en <https://console.cloud.google.com/> y crea un proyecto (por ejemplo `HugoTrain`).
2. En **APIs y servicios → Biblioteca**, habilita **Google Sheets API**.
3. En **Pantalla de consentimiento de OAuth** (o "Google Auth Platform"): tipo **Externo**, nombre `HugoTrain`, tu correo como soporte y contacto, y añade tu cuenta de Gmail como **usuario de prueba**.
4. En **Credenciales → Crear credenciales → ID de cliente de OAuth**, tipo **Android**:
   - Nombre del paquete: `com.hugobou.hugotrain`
   - Huella SHA-1: `33:C2:65:BD:B3:C4:83:C6:6A:39:4A:FB:6A:05:A4:29:5D:93:3D:4F`

No hace falta copiar ningún dato de Google en la app: Android lo identifica por el paquete y la huella.

### 2. Strava

1. En la app Garmin Connect, activa la conexión con Strava para que las actividades se sincronicen solas.
2. En <https://www.strava.com/settings/api> crea una aplicación: nombre `HugoTrain`, sitio web cualquiera y **Authorization Callback Domain** = `localhost`.
3. Copia el **Client ID** y el **Client Secret** en la pantalla Ajustes de la app (se guardan solo en el móvil).

### 3. Instalar la app

1. En el móvil, abre la [última versión](../../releases/tag/latest) y descarga `HugoTrain.apk`.
2. Ábrelo y permite "instalar apps de origen desconocido" cuando Android lo pida.
3. En **Ajustes** de la app: Conectar con Google → Crear hoja HugoTrain → Conectar Strava.

Cada cambio que se sube a `main` compila un APK nuevo automáticamente (GitHub Actions) y lo publica en esa misma release. Para actualizar, basta con descargarlo e instalarlo encima.

## Notas técnicas

- Kotlin + Jetpack Compose, `minSdk 26` (Android 8).
- Permiso de Google `drive.file`: la app solo ve las hojas que ella misma ha creado. Puedes editar esa hoja libremente desde Google Sheets en el ordenador.
- El APK se firma con `app/hugotrain.keystore` (clave de uso personal incluida en el repositorio para que la huella SHA-1 no cambie entre compilaciones). Si el repositorio fuera público algún día, conviene moverla a un secreto de GitHub.
