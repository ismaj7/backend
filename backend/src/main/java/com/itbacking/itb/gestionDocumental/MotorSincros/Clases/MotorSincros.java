package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.model.Condicion;
import com.itbacking.core.model.RegistroLog;
import com.itbacking.db.connection.Conexion;
import com.itbacking.db.connection.ConfiguracionConexion;
import com.itbacking.db.connection.TipoConexion;
import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSalida;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarEntrada;
import com.itbacking.notify.Notificacion;
import jakarta.mail.MessagingException;
import org.jvnet.hk2.annotations.Service;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

@EnableScheduling
@EnableAsync
@Service
public class MotorSincros {

    private Conexion conexionGestionDocumental;
    private ConectorDb conectorSincros;
    private ConectorDb conectorLogs;
    private ConectorDb conectorDocumentos;
    private ConectorDb conectorEtiquetas;
    private Coleccion parametros;

    public MotorSincros(Coleccion parametros) throws Exception {
        if (!parametros.containsKey("tablaSincros")) {
            throw new Exception("El parámetro \"tablaSincros\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("tipoConexion")) {
            throw new Exception("El parámetro \"tipoConexion\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("servidorConexion")) {
            throw new Exception("El parámetro \"servidorConexion\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("bdConexion")) {
            throw new Exception("El parámetro \"bdConexion\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("usuarioConexion")) {
            throw new Exception("El parámetro \"usuarioConexion\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("passwordConexion")) {
            throw new Exception("El parámetro \"passwordConexion\" no se encuentra entre los parámetros de conexión");
        }

        if (!parametros.containsKey("puertoConexion")) {
            throw new Exception("El parámetro \"puertoConexion\" no se encuentra entre los parámetros de conexión");
        }

        (parametros.aCadena()).log("Parametros");
        this.parametros = parametros;

        asegurarConexion();
    }

    public void procesarSincronizaciones() throws Exception {

        "Inicio Motor Sincronizaciones".cabeceraDeLog().log();

        //Ataque a la base de datos:
        ("Leyendo sincronizaciones de la base de datos \"" + parametros.get("bdConexion").aCadena() + "\"...").log();
        var sincronizaciones = leerSincronizacionesAProcesar();
        "Sincronizaciones obtenidas con éxito.".log();

        for(Map<String, Object> sincronizacion : sincronizaciones) {
            "Procesando sincronizacion".cabeceraDeLog().log();
            sincronizacion.log("Parametros");
            procesarSincronizacion(sincronizacion);
            "Fin Procesando sincronizacion".cabeceraDeLog().log();
        }

        "Fin Motor Sincronizaciones".cabeceraDeLog().log();
    }

    private void procesarSincronizacion(Map<String, Object> sincronizacion) throws Exception {

        //Inicializamos el log que más tarde registraremos en BD.
        var motorSincrosLog = new MotorSincrosLog();
        App.agregarRegistroLog(motorSincrosLog);

        //Creamos las carpetas de trabajo si no existe:
        crearCarpetaDeTrabajo(sincronizacion);

        //Procesamos el archivo y devolvemos una colección con el nombre del arhivo y un boolean de si la operación ha ido bien o no.
        var archivoProcesadoInfoLog = procesarArchivoSincronizacion(sincronizacion);
        var nombreArchivo = archivoProcesadoInfoLog.get("archivo").aCadena();
        var esExitoso = archivoProcesadoInfoLog.get("esExitoso").aBoolean();

        if(nombreArchivo != "") {

            "Registrando log".logDebug();
            registrarLogEnBD(sincronizacion, nombreArchivo, motorSincrosLog.logProceso, esExitoso);
            "Registrando log".logDebug();

            "Enviando notifiacion...".log();
            enviarNotificaciones(sincronizacion, nombreArchivo, esExitoso);
            "Notifiaciones enviadas.".log();

        }

    }

    private Coleccion procesarArchivoSincronizacion(Map<String, Object> sincronizacion) throws Exception {

        //Obtenemos la configuracion de la sincro
        var conf = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaDeTrabajo = conf.get("carpetaDeTrabajo").aCadena();

        var resultado = new Coleccion();

        //Primero buscamos el documento a analizar en la carpeta documentoEnProceso:
        var documentoParaProcesar = recuperarDocumentoPendiente(sincronizacion);
        if(documentoParaProcesar.esNulo()) {
            //Si no se encuentran documentos pendientes, se buscan en la carpeta inicial.
            "No se encontraron documentos pendientes".log();
            documentoParaProcesar = seleccionarDocumento(sincronizacion);
        }

        if(!documentoParaProcesar.esNulo()){
            resultado.asignar("archivo", documentoParaProcesar.nombreConExtension());
            resultado.asignar("esExitoso", procesarEntrada(documentoParaProcesar, sincronizacion));
        }else {
            "No se encontraron documentos en la carpeta original".log();
        }

        //Finalmente, limpiamos las carpetas:
        limpiarCarpeta(rutaCarpetaDeTrabajo.combinarRuta("documentoEnProceso"));
        limpiarCarpeta(rutaCarpetaDeTrabajo.combinarRuta("paginasSeparadas"));

        return resultado;

    }

    private void limpiarCarpeta(String rutaCarpeta) throws IOException {

        var carpeta = new File(rutaCarpeta);

        for(var archivo : carpeta.listFiles()) {
            archivo.delete();
        }

    }

    private void crearCarpetaDeTrabajo(Map<String, Object> sincronizacion) throws Exception {

        var confProcesarSincro = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaDeTrabajo = confProcesarSincro.get("carpetaDeTrabajo").aCadena();

        var carpetaDeTrabajo = new File(rutaCarpetaDeTrabajo);

        if(!carpetaDeTrabajo.exists()) {
            "Creando carpeta temporal...".logDebug();
            rutaCarpetaDeTrabajo.logDebug("Ruta carpeta temporal");
            carpetaDeTrabajo.mkdir();
            "Carpeta temporal creada con éxito".logDebug();
        }

        if(!carpetaDeTrabajo.isDirectory()) {
            throw new Exception("La ruta para la carpeta de trabajo no hace referencia a una carpeta sino a un archivo.");
        }

        crearSubcarpetasTemporales(rutaCarpetaDeTrabajo);
    }

    private void crearSubcarpetasTemporales(String rutaCarpetaDeTrabajo) {

        //Creamos las carpetas necesarias dentro de la ruta temporal.

        var pngTemporales = new File(rutaCarpetaDeTrabajo.combinarRuta("PNGsTemporales"));
        if(!pngTemporales.exists()) {
            ("Creando " + pngTemporales.aCadena()).logDebug();
            pngTemporales.mkdir();
            "Creada con éxito.".logDebug();
        }

        var documentosAProcesar = new File(rutaCarpetaDeTrabajo.combinarRuta("documentoEnProceso"));
        if(!documentosAProcesar.exists()) {
            ("Creando " + documentosAProcesar.aCadena()).logDebug();
            documentosAProcesar.mkdir();
            "Creada con éxito.".logDebug();
        }

        var paginasSeparadas = new File(rutaCarpetaDeTrabajo.combinarRuta("paginasSeparadas"));
        if(!paginasSeparadas.exists()) {
            ("Creando " + paginasSeparadas.aCadena()).logDebug();
            paginasSeparadas.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisar = new File(rutaCarpetaDeTrabajo.combinarRuta("Revisar"));
        if(!revisar.exists()) {
            ("Creando " + revisar.aCadena()).logDebug();
            revisar.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisarAviso = new File(rutaCarpetaDeTrabajo.combinarRuta("Revisar\\Aviso"));
        if(!revisarAviso.exists()) {
            ("Creando " + revisarAviso.aCadena()).logDebug();
            revisarAviso.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisarError = new File(rutaCarpetaDeTrabajo.combinarRuta("Revisar\\Error"));
        if(!revisarError.exists()) {
            ("Creando " + revisarError.aCadena()).logDebug();
            revisarError.mkdir();
            "Creada con éxito.".logDebug();
        }

    }
    
    private ProcesarEntrada obtenerInterfazProcesarEntrada(Map<String, Object> sincronizacion) throws Exception {

        var tipoProcesoSincro = sincronizacion.get("tipoProcesoSincro").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoSincro) {
            case "QR":
                return new ProcesarEntrada_QR();
            default :
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoSincro + "\"");

        }

    }

    private File recuperarDocumentoPendiente(Map<String, Object> sincronizacion) throws FileNotFoundException {

        //Devuelve (si lo hay) el documento que se quedó pendiente en la última sincronización

        var confProcesarSincro = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaDeTrabajo = confProcesarSincro.get("carpetaDeTrabajo").aCadena();
        var rutaCarpetaDocumentoEnProceso = rutaCarpetaDeTrabajo.combinarRuta("documentoEnProceso");
        var extensiones = confProcesarSincro.get("extensiones").aCadena().dividir(",");

        var carpetaDocumentoEnProceso = new File(rutaCarpetaDocumentoEnProceso);

        return documentoParaProcesar(carpetaDocumentoEnProceso, extensiones);

    }

    private boolean procesarEntrada(File archivoParaProcesar, Map<String, Object> sincronizacion) throws Exception {

        //Instancia e inicialización de la interfaz para procesar la sincro:
        var interfazProcesarEntrada = obtenerInterfazProcesarEntrada(sincronizacion);

        if(!archivoParaProcesar.esNulo()) {
            var resultados = interfazProcesarEntrada.procesarDocumentoEntrada(archivoParaProcesar, sincronizacion);
            return procesarSalida(resultados, sincronizacion);
        }else {
            return false;
        }
    }

    private boolean procesarSalida(List<ResultadoLectura> resultados, Map<String, Object> sincronizacion) throws Exception {

        //Procesamos el resultado si los hay
        if (!resultados.esNulo()) {

            //Instancia de la interfaz para procesar los resultados:
            ProcesarSalida interfazProcesarSalida = obtenerInterfazProcesarSalida(sincronizacion);

            //Procesamos los resultados.
            return interfazProcesarSalida.procesarSalida(resultados, sincronizacion);

        }else {
            return false;
        }

    }

    private ProcesarSalida obtenerInterfazProcesarSalida(Map<String, Object> sincronizacion) throws Exception {

        var tipoProcesoResultado = sincronizacion.get("tipoProcesoResultado").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoResultado) {
            case "XML_AVANBOX":
                return new ProcesarSalida_XMLAvanbox();
            case "GESTION_DOCUMENTAL":
                return new ProcesarSalida_GestionDocumental(conectorDocumentos, conectorEtiquetas);
            default :
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoResultado + "\"");

        }

    }

    private List<Map<String, Object>> leerSincronizacionesAProcesar() throws Exception {

        //Realizamos el SELECT con la condición de que esté activo:
        var condiciones=List.of(Condicion.igual("activo", 1));
        return conectorSincros.ejecutarSelect(null, condiciones);

    }

    private File seleccionarDocumento(Map<String, Object> sincronizacion) throws FileNotFoundException {

        //Devuelve el primer documento candidato que encuentra en la carpeta de origen.

        var rutaCarpetaInicial = sincronizacion.get("carpetaOrigen").aCadena();
        var confProcesarSincro = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var extensiones = confProcesarSincro.get("extensiones").aCadena().dividir(",");

        var carpeta = new File(rutaCarpetaInicial);
        return documentoParaProcesar(carpeta, extensiones);

    }

    private File documentoParaProcesar(File carpeta, List<String> extensiones) throws FileNotFoundException {

        //Dada una carpeta y unas extensiones válidas, nos devuelve el primer archivo que encuentre candidato para ser procesado.

        //Si no encuentra ningún archivo devuelve null;
        File archivoParaAnalizar = null;

        //Comprobamos que la carpeta exista o no
        if(!carpeta.exists()) {
            "No se puede encontrar la carpeta introducida.".logError(carpeta.nombreCompletoConRuta());
            "Fin servicio".cabeceraDeLog().log();
            throw new FileNotFoundException();
        }else {

            //Recorremos los archivos de la carpeta en busca del candidato.
            for(File archivoDeLaCarpeta : carpeta.listFiles()) {
                if(extensiones.contains(archivoDeLaCarpeta.extension())) {
                    archivoParaAnalizar = archivoDeLaCarpeta;
                    break;
                }
            }

            return archivoParaAnalizar;

        }
    }

    protected Conexion crearConexion() {

        //Obtener los parámetros de conexion de la variable parametrosConexion y establece la conexión.
        var tipo = parametros.get("tipoConexion").aCadena();
        var servidor = parametros.get("servidorConexion").aCadena();
        var bd = parametros.get("bdConexion").aCadena();
        var user = parametros.get("usuarioConexion").aCadena();
        var password = parametros.get("passwordConexion").aCadena();
        var puerto = parametros.get("puertoConexion").aCadena();

        var confConexion = new ConfiguracionConexion(TipoConexion.valueOf(tipo), servidor, bd, user, password, puerto.aInteger());
        return confConexion.crearConexion();

    }

    protected void asegurarConexion() {

        //Si no se ha establecido conexión todavía, establece una.
        if(conexionGestionDocumental != null) return;
        conexionGestionDocumental = crearConexion();

        //Obtenemos el nombre de las tablas necesarias:
        var repoSincros = parametros.get("tablaSincros").aCadena();
        var repoDocumentos = parametros.get("tablaDocumentos").aCadena();
        var repoEtiquetas = parametros.get("tablaEtiquetas").aCadena();
        var repoLogs = parametros.get("tablaLogs").aCadena();

        //Se establece la conexión:
        conectorSincros=conexionGestionDocumental.obtenerConectorDb(repoSincros);
        conectorDocumentos = conexionGestionDocumental.obtenerConectorDb(repoDocumentos);
        conectorEtiquetas = conexionGestionDocumental.obtenerConectorDb(repoEtiquetas);
        conectorLogs =conexionGestionDocumental.obtenerConectorDb(repoLogs);
    }

    private void enviarNotificaciones(Map<String, Object> sincronizacion, String nombreArchivo, boolean esExitoso) throws TelegramApiException, MessagingException {

        var confNotificacion = sincronizacion.get("confNotificacion").aCadena().aObjeto(Coleccion.class);

        confNotificacion.asignar("asunto", "SINCRO COMPLETA");
        var mensaje = "";
        if(esExitoso) {
            mensaje = nombreArchivo + "--> procesado con éxito.";
        }else {
            mensaje =nombreArchivo + "--> error.";
        }
        confNotificacion.asignar("mensaje", mensaje);

        var plataformasNotificacion = confNotificacion.get("plataforma").aCadena().aMayusculas().dividir(",");
        if(plataformasNotificacion.contains("EMAIL")) {
            confNotificacion.asignar("usuario", parametros.get("usuario").aCadena());
            confNotificacion.asignar("password", parametros.get("password").aCadena());
            confNotificacion.asignar("destino", parametros.get("destino").aCadena());
        }

        var notificacion = new Notificacion(confNotificacion, ",");

        notificacion.enviarNotificacion(confNotificacion);

    }

    private void registrarLogEnBD(Map<String, Object> sincronizacion, String documentoProcesado, String log, boolean resultadoEsCorrecto) throws Exception {

        "Registrando log en BD...".logDebug();

        var guidFila = sincronizacion.get("guid").aCadena();
        var guidOperacion = UUID.randomUUID();
        var nombreSincro = sincronizacion.get("nombre").aCadena();
        var fecha = new Date(); //Automáticamente se instancia con la fecha actual.

        var valoresAsignar = new Coleccion();
        valoresAsignar.asignar("nombreSincro", nombreSincro);
        valoresAsignar.asignar("documentoProcesado", documentoProcesado);
        valoresAsignar.asignar("id", guidOperacion);
        valoresAsignar.asignar("referencia", guidFila);
        valoresAsignar.asignar("fecha", fecha);
        valoresAsignar.asignar("correcto", resultadoEsCorrecto);
        valoresAsignar.asignar("log", log);

        //Map<String, Object>
        conectorLogs.ejecutarInsert(valoresAsignar);

        "Log registrado con éxito...".logDebug();

    }

    private class MotorSincrosLog implements RegistroLog {

        //TODO traducir esto: StringExtensions.saltoLinea() (buscar en el core que tiene que estar)

        public String logProceso = "";

        @Override
        public void log(String s, String s1) {
            var logProceso = this.logProceso;
            this.logProceso = logProceso + "(INFO)\t" + s1 + "\n";
        }

        @Override
        public void logAviso(String s, String s1) {
            var logProceso = this.logProceso;
            this.logProceso = logProceso + "(WARN)\t" + s1 + "\n";
        }

        @Override
        public void logDebug(String s, String s1) {
            var logProceso = this.logProceso;
            this.logProceso = logProceso + "(DEBUG)\t" + s1 + "\n";
        }

        @Override
        public void logError(String s, String s1) {
            var logProceso = this.logProceso;
            this.logProceso = logProceso + "(ERROR)\t" + s1 + "\n";
        }
    }

}
