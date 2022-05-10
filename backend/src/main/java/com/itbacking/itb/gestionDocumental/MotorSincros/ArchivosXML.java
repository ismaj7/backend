package com.itbacking.itb.gestionDocumental.MotorSincros;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.collection.Diccionario;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ArchivosXML implements ProcesarResultados {

    private String rutaCarpetaResultado;

    //Atributos de ServidorQR.cs
    private enum FormatoXML{Desconocido, Avanbox, ITB}
    private FormatoXML formato;
    private String mTextoQR;
    private final String saltoDeLinea = "\n";
    private Diccionario codigosQRITB = new Diccionario();
    private Diccionario valores = new Diccionario();
    private boolean error;
    private String descripcionError;

    //region Constructores:

    public ArchivosXML() {
        codigosQRITB.asignar("1", "ORIGEN");
        codigosQRITB.asignar("2", "CLAVE");
        codigosQRITB.asignar("3", "TIPO");
        codigosQRITB.asignar("4", "MODO");
        codigosQRITB.asignar("5", "ADICIONAL");
        codigosQRITB.asignar("6", "EXPEDIENTE1");
        codigosQRITB.asignar("7", "TIPOEXPEDIENTE1");
        codigosQRITB.asignar("8", "EXPEDIENTE2");
        codigosQRITB.asignar("9", "TIPOEXPEDIENTE2");
        codigosQRITB.asignar("A", "BD");
    }

    //TODO no entiendo muy bien por qué se implementa esto así...
    public ArchivosXML(String pTextoQR) {
        this.formato = formatoDelQR(pTextoQR);
        if(this.formato == FormatoXML.Avanbox)
            parsearFormatoAvanbox(pTextoQR);
        else if(this.formato == FormatoXML.ITB)
            parsearFormatoITB(pTextoQR);

        mTextoQR = pTextoQR;
    }

    //endregion

    //region Traídos desde ServidorQR.cs:

    private FormatoXML formatoDelQR(String pTextoQR) {
        if (pTextoQR.comienzaPor("<T\tsc_add>\r" + saltoDeLinea)) {
            return FormatoXML.Avanbox;
        } else if (pTextoQR.comienzaPor("ITB\r" + saltoDeLinea)) {
            return FormatoXML.ITB;
        }
        return FormatoXML.Desconocido;
    }

    private void parsearFormatoAvanbox(String pTexto) {
        var nLinNAV = pTexto.derechaDeOcurrencia("<S\tNAV\t").izquierdaDeOcurrencia(">" + saltoDeLinea);
        var nLinExp = pTexto.dividir(saltoDeLinea).listaCondicion(x->x.comienzaPor("<S\tEXPEDIENTE\t")).listaValor(x-> x.derechaDeOcurrencia("<S\tEXPEDIENTE\t").izquierdaDeOcurrencia(">"));
        var nValNAV = nLinNAV.dividir("\t");

        for(var nPar : nValNAV) {
            var nLstPar = nPar.dividir("=");
            if (nLstPar.longitud() == 2) {
                this.valores.asignar(nLstPar[0], nLstPar[1]);
            } else {
                this.descripcionError += (descripcionError != "" ? saltoDeLinea : "") + "Linea sin pares con texto: " + nPar;
                this.error = true;
            }
        }

        if(nLinExp.longitud()==2) {
            //2 expedientes
            var nLstPar = nLinExp.get(0).dividir("\t");
            this.valores.asignar("EXPEDIENTE2", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE2", nLstPar[1]);

            nLstPar = nLinExp.get(1).dividir("\t");
            this.valores.asignar("EXPEDIENTE1", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE1", nLstPar[1]);
        }
        else {
            var nLstPar = nLinExp.get(0).dividir("\t");
            this.valores.asignar("EXPEDIENTE1", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE1", nLstPar[1]);
        }

    }

    private void parsearFormatoITB(String pTexto) {
        var nCadValores = pTexto.derechaDeOcurrencia("ITB" + saltoDeLinea);
        var nLstCampos = nCadValores.dividir(saltoDeLinea);
        for (var nPar : nLstCampos) {
            var nLstPar = nPar.dividir("\t");
            if(nLstPar.longitud()==2) {
                var nNombre = codigosQRITB[nLstPar[0]];
                if (nNombre != null)
                    valores.asignar(nNombre, nLstPar[1]);
                else
                {
                    descripcionError += (descripcionError != "" ? saltoDeLinea : "") + "Codigo de campo incorrecto: " + nPar;
                    error = true;
                }
            }
            else
            {
                descripcionError += (descripcionError!=""? saltoDeLinea:"") + "Linea sin pares con texto: " + nPar;
                error = true;
            }
        }
    }

    private String obtenerXMLAvanbox() throws Exception {
        if (formato == FormatoXML.Desconocido) {
            throw new Exception("No se puede generar el xml del formato desconocido");
        }

        String nRes = "<?xml version=" + "\"1.0\"" + " encoding=" + "\"ISO-8859-1\"" + "?>" + saltoDeLinea;
        nRes += "<avanbox>" + saltoDeLinea;
        nRes += "<type>schema_add</type>" + saltoDeLinea;

        if (valores.get("TIPOEXPEDIENTE2").aCadena()!="")
        {
            nRes += seccionExpedienteXML("schema_1", 2);
            nRes += seccionExpedienteXML("schema_3", 1);
        } else
            nRes += seccionExpedienteXML("schema_1", 1);

        nRes += "<schema_2>" + saltoDeLinea;
        nRes += "<alias>NAV</alias>" + saltoDeLinea;
        nRes += "<dossier_id />" + saltoDeLinea;
        nRes += "<dossier_type />" + saltoDeLinea;

        nRes += seccionItemXML(1, "ORIGEN", valores.get("ORIGEN"));
        nRes += seccionItemXML(2, "CLAVE", valores.get("CLAVE"));
        nRes += seccionItemXML(3, "TIPO", valores.get("TIPO"));
        nRes += seccionItemXML(4, "ADICIONAL", valores.get("ADICIONAL"));
        nRes += seccionItemXML(5, "BD", valores.get("BD"));

        nRes += "</schema_2>" + saltoDeLinea;
        nRes += "<autoclass>EXPEDIENTE</autoclass>" + saltoDeLinea;
        nRes += "</avanbox>";
        return nRes;
    }

    private String seccionExpedienteXML(String pSchema,int pNumExpediente) {

        //Operaciones para formatear la fecha:
        var formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var fecha = LocalDateTime.now();

        String nRes = "";
        nRes += "<" + pSchema +">" + saltoDeLinea;
        nRes += "<alias>EXPEDIENTE</alias>" + saltoDeLinea;
        nRes += "<dossier_id>" + valores.get("EXPEDIENTE" + pNumExpediente) + "</dossier_id>" + saltoDeLinea;
        nRes += "<dossier_type>" + valores.get("TIPOEXPEDIENTE" + pNumExpediente) + "</dossier_type>" + saltoDeLinea;
        if (pNumExpediente == 1)
        {
            nRes += "<item_1>" + saltoDeLinea;
            nRes += "<alias>FECHA</alias>" + saltoDeLinea;
            nRes += "<value>" + (formatoFecha.format(fecha)) + "</value>" + saltoDeLinea;
            nRes += "</item_1>" + saltoDeLinea;
        }
        nRes += "</" + pSchema + ">" + saltoDeLinea;
        return nRes;
    }

    private String seccionItemXML(int pNumero, String pAlias, String pValor) {
        String nRes = "";
        nRes += "<item_" + pNumero+ ">" + saltoDeLinea;
        nRes += "<alias>" + pAlias+ "</alias>" + saltoDeLinea;
        nRes += "<value>" + pValor + "</value>" + saltoDeLinea;
        nRes += "</item_" + pNumero+ ">" + saltoDeLinea;
        return nRes;
    }

    //endregion

    @Override
    public void procesarResultados(Map<String, Object> fila, List<ResultadosLectura> resultados) throws Exception {

        "Generar Pares PDF-XML".cabeceraDeLog().log();

        //Leemos la configuración de la base de datos:
        "Leyendo configuración...".log();
        var conf = fila.get("confProcesarResultado").aCadena().aObjeto(Coleccion.class);
        leerConfiguracion(conf);

        //TODO esto es un poco redundante...
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

        "Eliminando archivos temporales...".log();
        eliminarCarpetaTemporal(rutaCarpetaTemporal);
        "eliminados con éxito.".log();

        "Fin de Generar Pares PDF-XML".cabeceraDeLog().log();

    }

    private void generarParesPdfXml(List<ResultadosLectura> resultados) throws Exception {

        for (var resultado : resultados) {

            //La ruta del archivo temporal que previamente ha generado MotorSincros y que contiene los QRs válidos:
            var rutaArchivoTemporal = resultado.getRutaArchivo();
            var nombreArchivoTemporal = rutaArchivoTemporal.derechaDeUltimaOcurrencia("\\").izquierdaDeOcurrencia(".");

            //Lista de String con los valores que se han leído de los QR(s).
            var valoresQR = resultado.getValoresQR();

            //El contador de QRs que usaremos para nombrar a los XMLs resultantes.
            var contadorQRs = 1;
            for(var valorQR : valoresQR) {

                //Creamos una nueva instancia de esta clase y llamamos al otro constructor con el valor del QR.
                var archivoXML = new ArchivosXML(valorQR);

                //Lo que escribiremos en el archivo XML.
                var xmlContenido = "";

                switch (archivoXML.formato) {
                    case Avanbox:
                        xmlContenido = obtenerXMLAvanbox();
                        break;
                    case ITB:
                        xmlContenido = valorQR;
                        break;
                    case Desconocido:
                        "No se generará ningún XML porque el formato es desconocido".log();
                        break;
                }

                ("Generando XML(s)... (" + contadorQRs + "/" + valoresQR.longitud() + ")").logDebug();
                generarXMLs(xmlContenido, nombreArchivoTemporal, contadorQRs);
                "XML(s) generado(s) con éxito.".logDebug();

                contadorQRs++;

            }

            "Moviendo PDF válido a la ruta final...".logDebug();
            moverPDFRutaFinal(rutaArchivoTemporal);
            "PDF movido.".logDebug();

        }
    }

    private void moverPDFRutaFinal(String rutaArchivoTemporal) {

        //También necesitamos crear una instancia del archivo y almacenar su nombre para nombrar los XMLs más tarde
        var archivoTemporal = new File(rutaArchivoTemporal);
        var nombreArchivoTemporal = archivoTemporal.nombreSinExtension();

        //Movemos el archivo con los QRs a la carpeta con su(s) respectivo(s) XML:
        var rutaPDFFinal = rutaCarpetaResultado + "\\" + nombreArchivoTemporal + ".pdf";
        var pdfFinal = new File(rutaPDFFinal);
        archivoTemporal.renameTo(pdfFinal);
    }

    private void generarXMLs(String xmlContenido, String nombreArchivoTemporal, Integer contadorQRs) throws IOException {

        //Escribimos el XML en la carpeta de resultado.
        if (!xmlContenido.igual("")) {

            var nombreXMLResultado = (nombreArchivoTemporal + "_XML_QR" + contadorQRs + ".xml");
            var rutaXMLResultado = new File(rutaCarpetaResultado + "\\" + nombreXMLResultado);

            var fileWriter = new FileWriter(rutaXMLResultado);
            fileWriter.write(xmlContenido);
            fileWriter.close();

        }

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

    private void eliminarCarpetaTemporal(String rutaCarpetaTemporal) throws IOException {

        var carpetaTemporal = new File(rutaCarpetaTemporal);

        for(var archivo : carpetaTemporal.listFiles()) {
            archivo.delete();
        }

        FileUtils.deleteDirectory(carpetaTemporal);

    }

}
