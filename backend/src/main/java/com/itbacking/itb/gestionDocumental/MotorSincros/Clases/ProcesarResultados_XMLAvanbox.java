package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarResultados_Sincro;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
public class ProcesarResultados_XMLAvanbox implements ProcesarResultados_Sincro {

    private String rutaCarpetaResultado;

    @Override
    public void procesarResultados(Map<String, Object> fila, List<ResultadosLectura> resultados) throws Exception {

        "Procesar resultados para Avanbox".cabeceraDeLog().log();

        //Leemos la configuración de la base de datos:
        "Leyendo configuración...".log();
        var conf = fila.get("confProcesarResultado").aCadena().aObjeto(Coleccion.class);
        leerConfiguracion(conf);

        //Leemos también de la otra columna la ruta temporal para eliminarla al final.
        //No hace falta hacer comprobaciones porque esto ya lo hace ProcesarQR.java
        var conf2 = fila.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        var rutaCarpetaTemporal = conf2.get("carpetaTemporal").aCadena();
        "Configuración leída con éxito.".log();

        "Creando carpeta de resultados...".log();
        //Creamos la carpeta de resultado, cuya ruta la hemos obtenido de la BD:
        crearCarpetaResultado();
        "Carpeta creada con éxito.".log();

        "Generando pares PDF-XML...".log();
        //Recorremos los resultados teniendo en cuenta que en un mismo archivo puede haber más de un QR.
        generarParesPdfXml(resultados);
        "Pares generados.".log();

        rutaCarpetaResultado.log("Carpeta de resultados");

        //Eliminamos el pdf de la carpeta documentos a procesar
        limpiarCarpetadocumentoEnProceso(rutaCarpetaTemporal + "\\documentoEnProceso");

        "Fin Procesar resultados para Avanbox".cabeceraDeLog().log();

    }

    private void generarParesPdfXml(List<ResultadosLectura> resultados) throws Exception {

        for (var resultado : resultados) {

            //La ruta del archivo temporal que previamente ha generado MotorSincros y que contiene los QRs válidos:
            var rutaPDFTemporal = resultado.getRutaArchivo();

            //Lo que escribiremos en el archivo XML.
            var etiquetas=resultado.etiquetas;
            var xmlContenido = new ArchivoXmlQR(etiquetas).obtenerXMLAvanbox();

            var nombreArchivoFinal = etiquetas.get("TIPO") + "_" + etiquetas.get("EXPEDIENTE1");

            ("Generando XML...").logDebug();
            nombreArchivoFinal.logDebug("Archivo");
            xmlContenido.logDebug("Contenido");
            generarXMLs(xmlContenido, nombreArchivoFinal);
            "XML generado(s) con éxito.".logDebug();

            "Moviendo PDF válido a la ruta final...".logDebug();
            moverPDFRutaFinal(rutaPDFTemporal, nombreArchivoFinal);
            "PDF movido.".logDebug();

        }
    }

    private void moverPDFRutaFinal(String rutaPDFTemporal, String nombreArchivoFinal) throws IOException {

        var archivoPDF = new File(rutaCarpetaResultado.combinarRuta(nombreArchivoFinal + ".pdf"));
        if(archivoPDF.exists()) {
            archivoPDF = cambiarNombre(archivoPDF);
        }

        var rutaPDFFinal = archivoPDF.nombreCompletoConRuta();
        rutaPDFFinal.logDebug("Ruta destino");

        var origen = Paths.get(rutaPDFTemporal);
        var destino = Paths.get(rutaPDFFinal);

        Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);

    }

    private void generarXMLs(String xmlContenido, String nombreArchivo) throws IOException {

        //Escribimos el XML en la carpeta de resultado.
        if (!xmlContenido.igual("")) {

            var nombreXMLResultado = (nombreArchivo + ".xml");
            var xmlResultado = new File(rutaCarpetaResultado + "\\" + nombreXMLResultado);
            if(xmlResultado.exists()) {
                xmlResultado = cambiarNombre(xmlResultado);
            }


            var fileWriter = new FileWriter(xmlResultado);
            fileWriter.write(xmlContenido);
            fileWriter.close();

        }

    }

    private File cambiarNombre(File archivo) {

        var contadorArchivos = 1;
        var rutaArchivo = archivo.rutaCompleta();
        var nombreArchivo = archivo.nombreSinExtension();
        var extensionArchivo = archivo.nombreConExtension().derechaDeOcurrencia(".");

        var archivoFinal = new File(rutaArchivo.combinarRuta(nombreArchivo + "_" + contadorArchivos + "." + extensionArchivo));

        while(archivoFinal.exists()) {
            contadorArchivos++;
            archivoFinal = new File(rutaArchivo.combinarRuta(nombreArchivo + "_" + contadorArchivos + "." + extensionArchivo));
        }

        return archivoFinal;

    }

    private void leerConfiguracion(Coleccion conf) throws Exception {

        //Log de los parámetros.
        conf.log("Parametros");

        if (conf.containsKey("carpetaResultado")) {
            rutaCarpetaResultado = conf.get("carpetaResultado").aCadena();
        }else {
            throw new Exception("No se ha establecido la variable carpetaResultado en la configuración.");
        }

    }

    private void crearCarpetaResultado() throws Exception {

        var carpetaResultado = new File(rutaCarpetaResultado);

        if(!carpetaResultado.exists()) {
            carpetaResultado.mkdir();
        }

        //Checkeamos que la carpeta sea directorio y si no lo es lanzamos una excepción para que el usuario revise la configuración.
        if(!carpetaResultado.isDirectory()) {
            "Comprueba la configuración introducida: ".logError();
            throw new Exception("No se puede establecer la carpeta de resultado en la siguiente ruta: \"" + rutaCarpetaResultado + "\"");
        }

    }

    private void limpiarCarpetadocumentoEnProceso(String rutaCarpeta) throws IOException {

        var carpetaDocumentoEnProceso = new File(rutaCarpeta);

        for(var archivo : carpetaDocumentoEnProceso.listFiles()) {
            archivo.delete();
        }

    }

}
