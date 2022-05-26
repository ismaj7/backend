package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.model.Condicion;
import com.itbacking.core.model.RegistroLog;
import com.itbacking.db.connection.Conexion;
import com.itbacking.db.connection.ConfiguracionConexion;
import com.itbacking.db.connection.TipoConexion;
import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarResultados;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSincro;
import com.itbacking.notify.Notificacion;
import jakarta.mail.MessagingException;
import org.jvnet.hk2.annotations.Service;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

@EnableScheduling
@EnableAsync
@Service
public class MotorSincros {

    private Conexion conexionGestionDocumental;
    private ConectorDb conectorSincros;
    private ConectorDb conectorResultados;
    private Coleccion parametros;
    
    //Constructor:
/*    public MotorSincros(Coleccion parametros) throws Exception {

        inicializarParametros();
        asegurarConexion();
    }*/

    public MotorSincros(Coleccion parametros) throws Exception {
        if (!parametros.containsKey("tablaSincros")) {
            parametros.asignar("tablaSincros", "SincronizacionesQR");
        }

        if (!parametros.containsKey("tipoConexion")) {
            parametros.asignar("tipoConexion", "MySql");
        }

        if (!parametros.containsKey("servidorConexion")) {
            parametros.asignar("servidorConexion", "172.16.10.35");
        }

        if (!parametros.containsKey("bdConexion")) {
            parametros.asignar("bdConexion", "GestionDocumental");
        }

        if (!parametros.containsKey("usuarioConexion")) {
            parametros.asignar("usuarioConexion", "itb");
        }

        if (!parametros.containsKey("passwordConexion")) {
            parametros.asignar("passwordConexion", "ITB%itb01");
        }

        if (!parametros.containsKey("puertoConexion")) {
            parametros.asignar("puertoConexion", "3306");
        }

        (parametros.aCadena()).log("Parametros");
        this.parametros = parametros;

        asegurarConexion();
    }

    public void procesarSincronizaciones() throws Exception {
        "Inicio Procesar Sincros".cabeceraDeLog().log();

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

        "Fin MotorSincros".cabeceraDeLog().log();
    }

    private void procesarSincronizacion(Map<String, Object> sincronizacion) throws Exception {

        //Inicializamos el log que más tarde registraremos en BD.
        var motorSincrosLog = new MotorSincrosLog();
        App.agregarRegistroLog(motorSincrosLog);

        //Creamos las carpetas de trabajo:
        crearCarpetaTemporal(sincronizacion);

        //Instancia e inicialización de la interfaz para procesar la sincro:
        var interfazProcesarSincro = obtenerInterfazProcesarSincro(sincronizacion);

        //Procesamos el archivo y devolvemos una colección con el nombre del arhivo y un boolean de si la operación ha ido bien o no.
        var archivoProcesadoInfoLog = procesarArchivoSincronizacion(sincronizacion, interfazProcesarSincro);
        var nombreArchivo = archivoProcesadoInfoLog.get("archivo").aCadena();
        var esExitoso = archivoProcesadoInfoLog.get("esExitoso").aBoolean();

        registrarLogEnBD(sincronizacion, nombreArchivo, motorSincrosLog.logProceso, esExitoso);

        "Enviando notifiacion(es)...".log();
        enviarNotificaciones(sincronizacion, nombreArchivo, esExitoso);
        "Notifiaciones enviadas.".log();

    }

    private Coleccion procesarArchivoSincronizacion(Map<String, Object> sincronizacion, ProcesarSincro interfazProcesarSincro) throws Exception {

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
            resultado.asignar("esExitoso", procesarDocumento(documentoParaProcesar, interfazProcesarSincro, sincronizacion));
        }else {
            "No se encontraron documentos en la carpeta original".log();
        }

        return resultado;

    }

    private void crearCarpetaTemporal(Map<String, Object> sincronizacion) throws Exception {

        var confProcesarSincro = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaTemporal = confProcesarSincro.get("carpetaTemporal").aCadena();

        var carpetaTemporal = new File(rutaCarpetaTemporal);

        if(!carpetaTemporal.exists()) {
            "Creando carpeta temporal...".logDebug();
            rutaCarpetaTemporal.logDebug("Ruta carpeta temporal");
            carpetaTemporal.mkdir();
            "Carpeta temporal creada con éxito".logDebug();
        }

        if(!carpetaTemporal.isDirectory()) {
            throw new Exception("La ruta para la carpeta de trabajo no hace referencia a una carpeta sino a un archivo.");
        }

        crearSubcarpetasTemporales(rutaCarpetaTemporal);
    }

    private void crearSubcarpetasTemporales(String rutaCarpetaTemporal) {

        //Creamos las carpetas necesarias dentro de la ruta temporal.

        var pngTemporales = new File(rutaCarpetaTemporal.combinarRuta("PNGsTemporales"));
        if(!pngTemporales.exists()) {
            ("Creando " + pngTemporales.aCadena()).logDebug();
            pngTemporales.mkdir();
            "Creada con éxito.".logDebug();
        }

        var documentosAProcesar = new File(rutaCarpetaTemporal.combinarRuta("documentoEnProceso"));
        if(!documentosAProcesar.exists()) {
            ("Creando " + documentosAProcesar.aCadena()).logDebug();
            documentosAProcesar.mkdir();
            "Creada con éxito.".logDebug();
        }

        var paginasSeparadas = new File(rutaCarpetaTemporal.combinarRuta("paginasSeparadas"));
        if(!paginasSeparadas.exists()) {
            ("Creando " + paginasSeparadas.aCadena()).logDebug();
            paginasSeparadas.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisar = new File(rutaCarpetaTemporal.combinarRuta("Revisar"));
        if(!revisar.exists()) {
            ("Creando " + revisar.aCadena()).logDebug();
            revisar.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisarAviso = new File(rutaCarpetaTemporal.combinarRuta("Revisar\\Aviso"));
        if(!revisarAviso.exists()) {
            ("Creando " + revisarAviso.aCadena()).logDebug();
            revisarAviso.mkdir();
            "Creada con éxito.".logDebug();
        }

        var revisarError = new File(rutaCarpetaTemporal.combinarRuta("Revisar\\Error"));
        if(!revisarError.exists()) {
            ("Creando " + revisarError.aCadena()).logDebug();
            revisarError.mkdir();
            "Creada con éxito.".logDebug();
        }

    }
    
    private ProcesarSincro obtenerInterfazProcesarSincro(Map<String, Object> sincronizacion) throws Exception {

        var tipoProcesoSincro = sincronizacion.get("tipoProcesoSincro").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoSincro) {
            case "QR":
                return new ProcesarSincro_QR();
            case "XML":
                return new ProcesarSincro_XML();
            default :
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoSincro + "\"");

        }

    }

    private File recuperarDocumentoPendiente(Map<String, Object> sincronizacion) throws FileNotFoundException {

        //Devuelve (si lo hay) el documento que se quedó pendiente en la última sincronización

        var confProcesarSincro = sincronizacion.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaTemporal = confProcesarSincro.get("carpetaTemporal").aCadena();
        var rutaCarpetaDocumentoEnProceso = rutaCarpetaTemporal.combinarRuta("documentoEnProceso");
        var extensiones = confProcesarSincro.get("extensiones").aCadena().dividir(",");

        var carpetaDocumentoEnProceso = new File(rutaCarpetaDocumentoEnProceso);

        return documentoParaProcesar(carpetaDocumentoEnProceso, extensiones);

    }

    private boolean procesarDocumento(File archivoParaProcesar, ProcesarSincro interfazProcesarSincro, Map<String, Object> sincronizacion) throws Exception {

        if(!archivoParaProcesar.esNulo()) {
            var resultados = interfazProcesarSincro.analizarDocumento(archivoParaProcesar, sincronizacion);
            return procesarResultadoLectura(resultados, sincronizacion);
        }else {
            return false;
        }
    }

    private boolean procesarResultadoLectura(List<ResultadoLectura> resultados, Map<String, Object> sincronizacion) throws Exception {

        //Procesamos el resultado si los hay
        if (!resultados.esNulo()) {
            //Instancia de la interfaz para procesar los resultados:
            ProcesarResultados interfazProcesarResultados = obtenerInterfazProcesarResultados(sincronizacion);

            //Procesamos los resultados.
            return interfazProcesarResultados.procesarResultados(resultados, sincronizacion);

        }else {
            return false;
        }

    }

    private ProcesarResultados obtenerInterfazProcesarResultados(Map<String, Object> sincronizacion) throws Exception {

        var tipoProcesoResultado = sincronizacion.get("tipoProcesoResultado").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoResultado) {
            case "XML_AVANBOX":
                return new ProcesarResultados_XMLAvanbox();
            case "GESTION_DOCUMENTAL":
                return new ProcesarResultados_GestionDocumental();
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
        var repoLogs = parametros.get("tablaLogs").aCadena();

        //Se establece la conexión:
        conectorSincros=conexionGestionDocumental.obtenerConectorDb(repoSincros);
        conectorResultados=conexionGestionDocumental.obtenerConectorDb(repoLogs);
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
        conectorResultados.ejecutarInsert(valoresAsignar);

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
