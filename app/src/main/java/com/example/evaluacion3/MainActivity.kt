package com.example.evaluacion3

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime
import java.time.format.TextStyle

class MainActivity : ComponentActivity() {

    // ViewModel para manejar la lógica de la cámara y la aplicación
    val camaraVM: AppVM by viewModels()
    // ViewModel para manejar la lógica del formulario de registro
    val formReigstroVM: FormReigstroVM by viewModels()

    // Controlador de la cámara para manejar el ciclo de vida de la cámara
    lateinit var cameraController: LifecycleCameraController

    // Lanzador de permisos para solicitar múltiples permisos
    val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Comprobar si ambos permisos (cámara y ubicación) fueron otorgados
        if (permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            camaraVM.OnPermisoCamaraOk() // Llamar a la función de permiso de cámara otorgado
            camaraVM.OnPermisoUbicacionOk() // Llamar a la función de permiso de ubicación otorgado
        } else {
            // Mostrar un mensaje si no se otorgaron los permisos necesarios
            Toast.makeText(this, "Se requieren permisos para la cámara y la ubicación", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Habilitar el diseño de borde a borde

        // Configuración del controlador de la cámara
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this) // Vincular el controlador de la cámara al ciclo de vida de la actividad
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // Seleccionar la cámara trasera por defecto

        // Establecer el contenido de la actividad utilizando Jetpack Compose
        setContent {
            AppUI(lanzadorPermisos, cameraController) // Llamar a la función composable principal pasando los lanzadores de permisos y el controlador de la cámara
        }
    }
}

// ViewModel para manejar el estado de la aplicación
class AppVM : ViewModel() {
    // Estado mutable que guarda la pantalla actual de la aplicación
    val pantallaActual = mutableStateOf(Pantalla.FORM)
    // Función lambda que se ejecuta cuando se otorgan los permisos de cámara
    var OnPermisoCamaraOk: () -> Unit = {}
    // Función lambda que se ejecuta cuando se otorgan los permisos de ubicación
    var OnPermisoUbicacionOk: () -> Unit = {}
}

// ViewModel para manejar el estado y la lógica del formulario de registro
class FormReigstroVM : ViewModel() {
    // Estado mutable que guarda el nombre del lugar visitado
    val nombre = mutableStateOf(TextFieldValue(""))
    // Lista mutable que guarda los nombres de los lugares visitados
    val lugares = mutableStateListOf<String>()
    // Lista mutable que guarda las URIs de las fotos tomadas
    val fotos = mutableStateListOf<Uri>()
    // Mapa mutable que asocia cada lugar visitado con una lista de URIs de fotos
    val fotosPorLugar = mutableStateMapOf<String, MutableList<Uri>>()
    // Estado mutable que guarda la latitud del lugar seleccionado
    val latitud = mutableStateOf(0.0)
    // Estado mutable que guarda la longitud del lugar seleccionado
    val longitud = mutableStateOf(0.0)
    // Estado mutable que guarda la URI de la foto que se va a expandir
    val fotoExpandida = mutableStateOf<Uri?>(null)
    // Estado mutable que guarda el marcador del mapa
    val marcador = mutableStateOf<Marker?>(null)

    // Función para guardar el nombre del lugar visitado
    fun guardarNombreDelLugar() {
        // Verifica si el nombre del lugar no está vacío
        if (nombre.value.text.isNotEmpty()) {
            // Agrega el nombre del lugar a la lista de lugares
            lugares.add(nombre.value.text)
            // Inicializa una lista de fotos para el nuevo lugar
            fotosPorLugar[nombre.value.text] = mutableListOf()
            // Limpia el campo de texto del nombre del lugar
            nombre.value = TextFieldValue("")
        }
    }

    // Función para agregar una foto a la lista de fotos del último lugar visitado
    fun agregarFoto(uri: Uri) {
        // Verifica si hay algún lugar visitado en la lista
        if (lugares.isNotEmpty()) {
            // Obtiene el último lugar visitado
            val ultimoLugar = lugares.last()
            // Agrega la URI de la foto a la lista de fotos del último lugar
            fotosPorLugar[ultimoLugar]?.add(uri)
        }
        // Agrega la URI de la foto a la lista general de fotos
        fotos.add(uri)
    }
}

// Enumera las posibles pantallas de la aplicación
enum class Pantalla {
    FORM,    // Pantalla del formulario
    CAMARA,  // Pantalla de la cámara
    MAPA     // Pantalla del mapa
}

// Función principal de la interfaz de usuario de la aplicación
@Composable
fun AppUI(
    lanzadorPermisos: ActivityResultLauncher<Array<String>>,  // Launcher para solicitar permisos
    cameraController: LifecycleCameraController  // Controlador del ciclo de vida de la cámara
) {
    val appVM: AppVM = viewModel()  // Obtiene la instancia del ViewModel compartido

    // Controla qué pantalla se muestra según el estado actual en el ViewModel
    when (appVM.pantallaActual.value) {
        Pantalla.FORM -> PantallaFormUI(lanzadorPermisos, cameraController)  // Muestra la pantalla del formulario
        Pantalla.CAMARA -> PantallaCamaraUI(lanzadorPermisos, cameraController)  // Muestra la pantalla de la cámara
        Pantalla.MAPA -> PantallaMapaUI(lanzadorPermisos)  // Muestra la pantalla del mapa
    }
}
@Composable
fun PantallaFormUI(
    lanzadorPermisos: ActivityResultLauncher<Array<String>>,  // Launcher para solicitar permisos
    cameraController: LifecycleCameraController  // Controlador del ciclo de vida de la cámara
) {
    val contexto = LocalContext.current  // Obtiene el contexto local
    val appVM: AppVM = viewModel()  // Obtiene la instancia del ViewModel compartido
    val formReigstroVM: FormReigstroVM = viewModel()  // Obtiene la instancia del ViewModel del formulario

    // Columna principal que organiza el contenido
    Column(modifier = Modifier.padding(16.dp)) {

        // Columna interna para el formulario de registro
        Column(modifier = Modifier.padding(10.dp)) {
            // Título del formulario
            Text(
                text = "Registro de Vacaciones",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp, top= 20.dp)
            )

            // Campo de texto básico para ingresar el nombre del lugar visitado
            BasicTextField(
                value = formReigstroVM.nombre.value,
                onValueChange = { formReigstroVM.nombre.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                decorationBox = { innerTextField ->
                    if (formReigstroVM.nombre.value.text.isEmpty()) {
                        Text(
                            "Nombre del lugar visitado",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Botón para guardar el nombre del lugar ingresado
            Button(onClick = {
                formReigstroVM.guardarNombreDelLugar()
            }) {
                Text(
                    text = "Guardar Lugar",
                    fontSize = 16.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Botón para cambiar a la pantalla de la cámara
            Button(onClick = {
                appVM.pantallaActual.value = Pantalla.CAMARA
            }) {
                Text(
                    text = "Tomar Foto",
                    fontSize = 16.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Botón para cambiar a la pantalla del mapa
            Button(onClick = {
                appVM.pantallaActual.value = Pantalla.MAPA
            }) {
                Text(
                    text = "Seleccionar Ubicación",
                    fontSize = 16.sp,
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
            // Muestra los lugares guardados y sus imágenes asociadas si existen
            if (formReigstroVM.lugares.isNotEmpty()) {
                Text(
                    text = "Lugares Guardados:",
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                formReigstroVM.lugares.forEach { lugar ->
                    // Muestra el nombre del lugar guardado
                    Text(
                        text = lugar,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 18.sp,
                    )
                    // Muestra las imágenes asociadas al lugar
                    val fotos = formReigstroVM.fotosPorLugar[lugar]
                    fotos?.forEach { uri ->
                        Image(
                            painter = BitmapPainter(uri2imageBitmap(uri, contexto)),
                            contentDescription = "Imagen tomada",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(4.dp)
                                .graphicsLayer(rotationZ = 90f)  // Aplica rotación a la imagen
                                .clickable {
                                    formReigstroVM.fotoExpandida.value = uri
                                }
                        )
                    }
                    // Muestra la latitud y longitud del lugar si están disponibles
                    val latitud = formReigstroVM.latitud.value
                    val longitud = formReigstroVM.longitud.value
                    if (latitud != 0.0 && longitud != 0.0) {
                        Text(
                            text = "Latitud: $latitud, Longitud: $longitud",
                            modifier = Modifier.padding(8.dp),
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
    }
    // Muestra la imagen expandida si hay una seleccionada
    formReigstroVM.fotoExpandida.value?.let { uri ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    formReigstroVM.fotoExpandida.value = null
                }
        ) {
            Image(
                painter = BitmapPainter(uri2imageBitmap(uri, contexto)),
                contentDescription = "Imagen expandida",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = 90f)  // Aplica rotación a la imagen
            )
        }
    }
    // Lanza la solicitud de permisos al iniciar la pantalla
    LaunchedEffect(Unit) {
        lanzadorPermisos.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }
}


@Composable
fun PantallaCamaraUI(
    lanzadorPermisos: ActivityResultLauncher<Array<String>>,  // Launcher para solicitar permisos
    cameraController: LifecycleCameraController  // Controlador del ciclo de vida de la cámara
) {
    val contexto = LocalContext.current  // Obtiene el contexto local
    val formReigstroVM: FormReigstroVM = viewModel()  // Obtiene la instancia del ViewModel del formulario
    val appVM: AppVM = viewModel()  // Obtiene la instancia del ViewModel compartido
    // Contenedor principal que ocupa todo el espacio disponible
    Box(modifier = Modifier.fillMaxSize()) {
        // Vista de Android para mostrar la vista previa de la cámara
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).apply {
                    controller = cameraController  // Asigna el controlador de la cámara a la vista previa
                }
            }
        )
        // Botón para capturar una foto, alineado en la parte inferior central
        Button(
            onClick = {
                capturarFoto(
                    cameraController,
                    crearArchivoImagenPrivado(contexto),  // Crea un archivo para guardar la imagen capturada
                    contexto
                ) {
                    formReigstroVM.agregarFoto(it)  // Agrega la foto capturada al ViewModel del formulario
                    appVM.pantallaActual.value = Pantalla.FORM  // Cambia a la pantalla de formulario
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)  // Alinea y ajusta el margen inferior del botón
        ) {
            Text(
                text = "Capturar Foto",
                fontSize = 20.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaMapaUI(lanzadorPermisos: ActivityResultLauncher<Array<String>>) {
    val contexto = LocalContext.current  // Obtiene el contexto local
    val formReigstroVM: FormReigstroVM = viewModel()  // Obtiene el ViewModel del formulario
    val appVM: AppVM = viewModel()  // Obtiene el ViewModel compartido

    Column(modifier = Modifier.fillMaxSize()) {  // Columna principal que ocupa todo el tamaño disponible
        TopAppBar(
            title = { Text("Mapa") },  // Título de la barra superior
            modifier = Modifier.padding(bottom = 20.dp),  // Ajuste del margen inferior
            navigationIcon = {
                IconButton(onClick = {
                    appVM.pantallaActual.value = Pantalla.FORM  // Acción al hacer clic en el ícono de navegación (volver a la pantalla de formulario)
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")  // Ícono de flecha hacia atrás
                }
            },
            actions = {
                IconButton(onClick = {
                    val marcador = formReigstroVM.marcador.value  // Obtiene el marcador del ViewModel del formulario
                    marcador?.let {
                        formReigstroVM.latitud.value = it.position.latitude  // Actualiza la latitud en el ViewModel del formulario con la posición del marcador
                        formReigstroVM.longitud.value = it.position.longitude  // Actualiza la longitud en el ViewModel del formulario con la posición del marcador
                        appVM.pantallaActual.value = Pantalla.FORM  // Cambia a la pantalla de formulario
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Seleccionar Ubicación")  // Ícono de confirmación para seleccionar la ubicación
                }
            }
        )

        // Vista de Android para mostrar el mapa
        AndroidView(
            modifier = Modifier.weight(1f),  // Ocupa el espacio restante en la columna
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)  // Configura el origen de los azulejos del mapa
                    org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName  // Configura el agente de usuario para el mapa
                    controller.setZoom(16.0)  // Establece el nivel de zoom inicial del mapa
                    val mapView = this

                    // Configura y añade un marcador al mapa
                    val marcador = Marker(this).apply {
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDragStart(marker: Marker?) {}
                            override fun onMarkerDrag(marker: Marker?) {}
                            override fun onMarkerDragEnd(marker: Marker?) {
                                marker?.let {
                                    formReigstroVM.latitud.value = it.position.latitude  // Actualiza la latitud en el ViewModel del formulario con la posición del marcador arrastrado
                                    formReigstroVM.longitud.value = it.position.longitude  // Actualiza la longitud en el ViewModel del formulario con la posición del marcador arrastrado
                                }
                            }
                        })
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)  // Establece el anclaje del marcador
                        isDraggable = true  // Permite arrastrar el marcador en el mapa
                    }
                    formReigstroVM.marcador.value = marcador  // Guarda el marcador en el ViewModel del formulario
                    mapView.overlays.add(marcador)  // Añade el marcador a las superposiciones del mapa

                    // Obtiene la ubicación actual y centra el mapa en esa ubicación
                    conseguirUbicacion(context) { location ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        marcador.position = geoPoint  // Establece la posición del marcador en la ubicación actual
                        controller.setCenter(geoPoint)  // Centra el mapa en la ubicación actual
                    }
                }
            },
            update = { mapView ->
                mapView.overlays.clear()  // Limpia las superposiciones existentes en el mapa
                mapView.invalidate()  // Invalida el mapa para que se redibuje

                val latitud = formReigstroVM.latitud.value  // Obtiene la latitud del ViewModel del formulario
                val longitud = formReigstroVM.longitud.value  // Obtiene la longitud del ViewModel del formulario
                val geoPoint = GeoPoint(latitud, longitud)  // Crea un punto geográfico con la latitud y longitud

                mapView.controller.setCenter(geoPoint)  // Centra el mapa en el punto geográfico

                val marcador = formReigstroVM.marcador.value  // Obtiene el marcador del ViewModel del formulario
                marcador?.position = geoPoint  // Actualiza la posición del marcador con el nuevo punto geográfico
                mapView.overlays.add(marcador)  // Añade el marcador a las superposiciones del mapa
            }
        )
    }
    // Efecto lanzado al inicio para solicitar permisos de ubicación
    LaunchedEffect(Unit) {
        lanzadorPermisos.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
}

// Define una excepción personalizada para manejar casos donde faltan permisos de ubicación.
class FaltaPermisosException(mensaje: String) : Exception(mensaje)

// Función para obtener la ubicación actual del dispositivo.
fun conseguirUbicacion(contexto: Context, onSuccess: (ubicacion: Location) -> Unit) {
    try {
        // Obtiene el proveedor de servicios de ubicación fusionada.
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)

        // Inicia una tarea para obtener la ubicación actual con alta precisión.
        val tarea = servicio.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )

        // Registra un callback para manejar el éxito de obtener la ubicación.
        tarea.addOnSuccessListener {
            onSuccess(it) // Llama a la función onSuccess con la ubicación obtenida.
        }
    } catch (se: SecurityException) {
        // Captura y maneja la excepción si faltan permisos de ubicación,
        // lanzando una FaltaPermisosException con un mensaje descriptivo.
        throw FaltaPermisosException("Falta permisos de ubicación")
    }
}

// Función para generar un nombre de archivo único basado en la fecha y hora actual hasta el segundo.
fun generarNombreSegunFechaHastasegundo(): String = LocalDateTime
    .now().toString().replace(Regex("[-:]"), "").substring(0, 14)

// Función para crear un archivo de imagen privado en el directorio de imágenes específico de la aplicación.
fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastasegundo()}.jpg"
)

// Función para capturar una foto usando el controlador de cámara del ciclo de vida proporcionado.
fun capturarFoto(
    cameraController: LifecycleCameraController,
    archivo: File, // Archivo donde se guardará la imagen capturada
    contexto: Context,
    onImagenGuardada: (uri: Uri) -> Unit // Callback que se llama cuando la imagen se guarda exitosamente
) {
    // Configura las opciones para guardar la imagen en el archivo especificado.
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()

    // Toma la foto utilizando el controlador de cámara.
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto), // Ejecutor principal para manejar callbacks en el hilo principal
        object : ImageCapture.OnImageSavedCallback {
            // Callback llamado cuando la imagen se guarda exitosamente.
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    onImagenGuardada(it) // Llama al callback con la URI de la imagen guardada.
                }
            }

            // Callback llamado si hay un error al guardar la imagen.
            override fun onError(exception: ImageCaptureException) {
                Log.e("Error::onImageCallback::onError", exception.message ?: "Error")
            }
        }
    )
}

// Función para convertir una URI de imagen en un Bitmap utilizando el contexto proporcionado.
fun uri2imageBitmap(uri: Uri, contexto: Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()
