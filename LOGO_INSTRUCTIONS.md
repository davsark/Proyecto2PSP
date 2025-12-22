# 📋 Instrucciones para Agregar el Logo Real

## Ubicación del Logo Temporal

Actualmente, la aplicación usa el icono `CloudDownload` de Material Icons como **placeholder temporal**.

## Pasos para Usar el Logo Real

### 1. Preparar el Logo

Necesitas tener el archivo del logo en formato **PNG** con fondo transparente:
- **Nombre sugerido:** `logo.png`
- **Tamaño recomendado:** 512x512px o 1024x1024px
- **Formato:** PNG con transparencia

### 2. Crear la Estructura de Directorios

Crea esta estructura si no existe:

```
composeApp/
└── src/
    └── commonMain/
        └── composeResources/
            └── drawable/
                └── logo.png    ← Coloca tu logo aquí
```

### 3. Agregar la Dependencia de Resources (Si no está)

En `composeApp/build.gradle.kts`, asegúrate de tener:

```kotlin
implementation(compose.components.resources)
```

### 4. Modificar el Código

#### Para Desktop (TopBar.kt)

Ubicación: `composeApp/src/jvmMain/kotlin/com/dam2/flashdownloader/ui/components/TopBar.kt`

Busca esta línea (~línea 60):

```kotlin
Icon(
    imageVector = Icons.Default.CloudDownload,
    contentDescription = "Flash Downloader Logo",
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(40.dp)
)
```

Reemplázala con:

```kotlin
Image(
    painter = painterResource(Res.drawable.logo),
    contentDescription = "Flash Downloader Logo",
    modifier = Modifier.size(40.dp)
)
```

Y agrega este import al inicio del archivo:

```kotlin
import org.jetbrains.compose.resources.painterResource
import flashdownloader.composeapp.generated.resources.Res
import flashdownloader.composeapp.generated.resources.logo
```

#### Para Android (DownloadApp.kt)

Ubicación: `composeApp/src/androidMain/kotlin/com/dam2/flashdownloader/ui/DownloadApp.kt`

Busca esta línea (~línea 84):

```kotlin
Icon(
    imageVector = Icons.Default.CloudDownload,
    contentDescription = "Flash Downloader Logo",
    modifier = Modifier.size(28.dp)
)
```

Reemplázala con:

```kotlin
Image(
    painter = painterResource(Res.drawable.logo),
    contentDescription = "Flash Downloader Logo",
    modifier = Modifier.size(28.dp)
)
```

Y agrega los mismos imports que en Desktop.

### 5. Limpiar y Reconstruir

```powershell
.\gradlew.bat clean
.\gradlew.bat :composeApp:run
```

## Alternativa: Usar el Logo Directamente (Solo Android)

Si solo quieres usar el logo en Android sin Compose Resources:

1. Coloca el logo en:
   ```
   composeApp/src/androidMain/res/drawable/logo.png
   ```

2. Usa este código en Android:
   ```kotlin
   Image(
       painter = painterResource(id = R.drawable.logo),
       contentDescription = "Flash Downloader Logo",
       modifier = Modifier.size(28.dp)
   )
   ```

## Notas

- El logo actual (icono temporal) se verá bien, pero es genérico
- El logo real debería reflejar la identidad de "Flash Downloader"
- Asegúrate de que el logo tenga buena visibilidad en modo claro y oscuro
- Si el logo tiene colores específicos, elimina el parámetro `tint` para que se vea con sus colores originales

## Verificación

Después de agregar el logo, deberías verlo en:

- ✅ **Desktop:** Esquina superior izquierda del TopBar, junto al título
- ✅ **Android:** TopAppBar, junto al título

Si hay errores de compilación, verifica:
1. Que el archivo esté en la ubicación correcta
2. Que el nombre del archivo sea exactamente `logo.png` (minúsculas)
3. Que hayas agregado los imports correctos
