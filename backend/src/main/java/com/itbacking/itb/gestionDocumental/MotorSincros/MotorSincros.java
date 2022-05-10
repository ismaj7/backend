package com.itbacking.itb.gestionDocumental.MotorSincros;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.model.Condicion;
import com.itbacking.db.connection.Conexion;
import com.itbacking.db.connection.ConfiguracionConexion;
import com.itbacking.db.connection.TipoConexion;
import com.itbacking.db.connector.ConectorDb;

import java.util.List;
import java.util.Map;

public class MotorSincros {

    private Conexion conexionGestionDocumental;
    private ConectorDb conector;
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

    public void iniciar() throws Exception {
        //Inicia el servidor:

        //Log de cabecera:
        "Inicio MotorSincros".cabeceraDeLog().log();

        //Ataque a la base de datos:
        ("Leyendo filas de la base de datos \"" + parametros.get("bdConexion").aCadena() + "\"...").log();
        var filasBD = leerRegistrosSincro();
        "Hecho".log();

        for(Map<String, Object> fila : filasBD) {
            procesarSincro(fila);
        }

        "Fin MotorSincros".cabeceraDeLog().log();

    }

    private void procesarSincro(Map<String, Object> fila) throws Exception {

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
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoSincro + "\"");

        }
        //Obtenemos los resultados de leer los archivos:
        var resultados = interfazProcesarSincro.procesarArchivos(fila);

        //Instancia de la interfaz para procesar los resultados:
        ProcesarResultados interfazProcesarResultados;

        //Leemos el tipo de procesado desde la BD:
        var tipoProcesoResultado = fila.get("tipoProcesoResultado").aCadena().aMayusculas();

        //Inicializaremos la interfaz según el tipo de proceso:
        switch(tipoProcesoResultado) {
            case "XML":
                interfazProcesarResultados = new ArchivosXML();
                break;
            case "GD":
                interfazProcesarResultados = new ArchivosGestionDocumental();
                break;
            default :
                throw new Exception("No se reconoce el tipo de proceso siguiente: \"" + tipoProcesoResultado + "\"");

        }

        //Procesamos los resultados.
        interfazProcesarResultados.procesarResultados(fila, resultados);


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

        //Obtenemos el nombre de la tabla necesarias:
        var repo = parametros.get("tablaSincros").aCadena();

        //Se establece la conexión:
        conector=conexionGestionDocumental.obtenerConectorDb(repo);
    }

    //endregion

    //Asegura la conexión y ejecuta el SELECT sobre la tabla. Se llama dentro de iniciar().
    //TODO aceptar parámetros como por ej. condiciones, orden, alias... ??
    private List<Map<String, Object>> leerRegistrosSincro() throws Exception {
        asegurarConexion();

        //Realizamos el SELECT con la condición de que esté activo:
        List<Map<String, Object>> resultado;
        var condiciones=List.of(Condicion.igual("activo", 1));
        resultado = conector.ejecutarSelect(null, condiciones);

        return resultado;

    }

}
