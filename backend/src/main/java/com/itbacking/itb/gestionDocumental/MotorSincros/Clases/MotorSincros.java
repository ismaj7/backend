package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.model.Condicion;
import com.itbacking.core.model.RegistroLog;
import com.itbacking.db.connection.Conexion;
import com.itbacking.db.connection.ConfiguracionConexion;
import com.itbacking.db.connection.TipoConexion;
import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarResultados_Sincro;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSincro;
import com.itbacking.notify.Notificacion;
import jakarta.mail.MessagingException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MotorSincros {

    private Conexion conexionGestionDocumental;
    private ConectorDb conectorSincros;
    private ConectorDb conectorResultados;
    private Coleccion parametros;

    //region Constructor e Inicio del servicio:

    public MotorSincros(Coleccion parametros) {

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
    }

    public void procesarSincros() throws Exception {

        //Log de cabecera:
        "Inicio MotorSincros".cabeceraDeLog().log();

        //Ataque a la base de datos:
        ("Obteniendo filas de la base de datos \"" + parametros.get("bdConexion").aCadena() + "\"...").log();
        var filasBD = leerRegistrosSincro();
        "Filas obtenidas".log();

        for(Map<String, Object> fila : filasBD) {
            "Procesando fila".cabeceraDeLog().log();
            procesarSincro(fila);
            "Fin Procesando fila".cabeceraDeLog().log();
        }

        "Fin MotorSincros".cabeceraDeLog().log();

    }

    private void procesarSincro(Map<String, Object> fila) throws Exception {

        //Inicializamos el log que más tarde registraremos en BD.
        var motorSincrosLog = new MotorSincrosLog();
        App.agregarRegistroLog(motorSincrosLog);

        //Instancia de la interfaz para procesar la sincro:
        ProcesarSincro interfazProcesarSincro;

        //Leemos el tipo de procesado desde la BD:
        var tipoProcesoSincro = fila.get("tipoProcesoSincro").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoSincro) {
            case "QR":
                interfazProcesarSincro = new ProcesarQR();
                break;
            case "XML":
                interfazProcesarSincro = new ProcesarXML();
                break;
            default :
                "Las opciones son: QR, XML".log();
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoSincro + "\"");

        }

        //Obtenemos los resultados de leer los archivos:
        var resultados = interfazProcesarSincro.procesarArchivos(fila);

        //Procesamos los resultados si los hay
        if (resultados.longitud() > 0) {
            //Instancia de la interfaz para procesar los resultados:
            ProcesarResultados_Sincro interfazProcesarResultados;

            //Leemos el tipo de procesado desde la BD:
            var tipoProcesoResultado = fila.get("tipoProcesoResultado").aCadena().aMayusculas();

            //Inicializaremos la interfaz según el tipo de proceso:
            switch(tipoProcesoResultado) {
                case "XML_AVANBOX":
                    interfazProcesarResultados = new ProcesarResultados_XMLAvanbox();
                    break;
                case "GESTION_DOCUMENTAL":
                    interfazProcesarResultados = new ProcesarResultados_GestionDocumental();
                    break;
                default :
                    throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoResultado + "\"");

            }

            //Procesamos los resultados.
            interfazProcesarResultados.procesarResultados(fila, resultados);

//            "Enviando notifiacion(es)...".log();
//            var confNotificacion = fila.get("confNotificacion").aCadena().aObjeto(Coleccion.class);
//            confNotificacion.asignar("usuario", "scara@itbacking.com");
//            confNotificacion.asignar("password", "ITB%sc02");
//            confNotificacion.asignar("destino", "ibahmane@itbacking.com");
//            confNotificacion.asignar("asunto", "Asunto Test");
//            confNotificacion.asignar("mensaje", "Cuerpo Texto Test");
//
//            enviarNotificaciones(confNotificacion);
//            "Notifiaciones enviadas.".log();
//
//            registrarResultadosEnBD(fila, motorSincrosLog.logProceso);

        }

    }

    //endregion

    //region Conexion

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

    protected void asegurarConexion() throws Exception {

        //Si no se ha establecido conexión todavía, establece una.
        if(conexionGestionDocumental != null) return;
        conexionGestionDocumental = crearConexion();

        //Obtenemos el nombre de las tablas necesarias:
        var repoSincros = parametros.get("tablaSincros").aCadena();
        var repoResultados = parametros.get("tablaResultados").aCadena();

        //Se establece la conexión:
        conectorSincros=conexionGestionDocumental.obtenerConectorDb(repoSincros);
        conectorResultados=conexionGestionDocumental.obtenerConectorDb(repoResultados);
    }

    //endregion

    private List<Map<String, Object>> leerRegistrosSincro() throws Exception {
        //Asegura la conexión y ejecuta el SELECT sobre la tabla. Se llama dentro de iniciar().
        asegurarConexion();

        //Realizamos el SELECT con la condición de que esté activo:
        List<Map<String, Object>> resultado;
        var condiciones=List.of(Condicion.igual("activo", 1));
        resultado = conectorSincros.ejecutarSelect(null, condiciones);

        return resultado;

    }

    private void enviarNotificaciones(Coleccion confNotificacion) throws TelegramApiException, MessagingException {

        confNotificacion.asignar("plataforma", confNotificacion.get("plataforma").aCadena().sustituir(",", ";"));

        var notificacion = new Notificacion(confNotificacion, confNotificacion);

        notificacion.enviarNotificacion(confNotificacion);

    }

    private void registrarResultadosEnBD(Map<String, Object> fila, String log) throws Exception {

        var guidFila = fila.get("guid").aCadena();
        var guidOperacion = UUID.randomUUID();
        var fecha = new Date(); //Automáticamente se instancia con la fecha actual.

        var valoresAsignar = new Coleccion();
        valoresAsignar.asignar("id", guidOperacion);
        valoresAsignar.asignar("referencia", guidFila);
        valoresAsignar.asignar("fecha", fecha);
        valoresAsignar.asignar("correcto", true);
        valoresAsignar.asignar("log", log);

        //Map<String, Object>
        conectorResultados.ejecutarInsert(valoresAsignar);

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
