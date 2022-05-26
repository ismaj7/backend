package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.code2d.LectorQR;
import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.collection.Diccionario;
import com.itbacking.core.model.RegistroLog;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSincro;
import com.itbacking.pdf.Pdf;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcesarQR implements ProcesarSincro {

    private String rutaCarpetaTemporal;
    private List<String> extensiones;
    private Integer tamañoMaxProcesarEnMemoria;
    private ProcesarQRLog procesarQRLog = new ProcesarQR.ProcesarQRLog();

    private Diccionario formatos = new Diccionario();
    @Override
    public List<ResultadoLectura> analizarDocumento(File archivo, Map<String, Object> fila) throws Exception {

        var conf = fila.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        leerConfiguracion(conf);

        return analizarArchivo(archivo);

    }

    private List<ResultadoLectura> analizarArchivo(File archivo) throws IOException {

        var resultadoFinal = new ArrayList<ResultadoLectura>();

        App.agregarRegistroLog(procesarQRLog);

        //Movemos el archivo a nuestra carpeta.
        var archivoAnalizando = new File(rutaCarpetaTemporal + "\\documentoEnProceso\\" + archivo.nombreConExtension());
        archivo.renameTo(archivoAnalizando);
        //Extraemos del archivo la info que necesitamos:
        var nombreDelArchivo = archivoAnalizando.nombreConExtension();
        Long tamañoArchivoBytes =  archivoAnalizando.length();
        var tamañoArchivo = tamañoArchivoBytes.aDouble()/1000000; // dividimos entre 1000000 para pasarlo a MB

        //Creamos la instancia de la clase Pdf con la ruta del archivo:
        var pdf = new Pdf(archivoAnalizando.nombreCompletoConRuta());

        //En esta colección guardaremos el resultado de procesarPDF
        //LLave: Número de página - Valor: String con el resultado de leer el QR
        var resultadoProcesarPDF = new HashMap<Integer, String>();

        //Si el tamaño es mayor del que pone en la BD, se hará en disco y si no en memoria:
        if (tamañoArchivo > tamañoMaxProcesarEnMemoria) {
            ("Analizando " + archivoAnalizando.nombreConExtension() + " en disco" + " (" + tamañoArchivo +" MB)...").log();
            resultadoProcesarPDF = procesarPDFEnDisco(pdf);
            eliminarPNGsIntermedios();
            "Hecho".log();
        }else {
            ("Analizando " + archivoAnalizando.nombreConExtension() + " en memoria" + " (" + tamañoArchivo +" MB)...").log();
            resultadoProcesarPDF = procesarPDFEnMemoria(pdf);
            "Hecho".log();
        }

        //Almacenaremos aquí el número de las páginas del PDF que contengan un QR y los resultados de las lecturas:
        var paginasQueContienenQR = resultadoProcesarPDF.listaValor(x -> x.getKey());
        var resultadosLecturaQRs = resultadoProcesarPDF.listaValor(x -> x.getValue());

        //Si paginasQueContienenQR está vacío significa que no se han encontrado QRs en el documento.
        if (!paginasQueContienenQR.isEmpty()) {
            //Dividimos el PDF original en diversos PDFs con las páginas que contienenQR
            var rutasArchivosSeparados = pdf.dividirPDFPorNumerosDePagina(paginasQueContienenQR, (rutaCarpetaTemporal + "\\paginasSeparadas"));

            if(paginasQueContienenQR.longitud() < rutasArchivosSeparados.longitud()) {
                "Primera(s) pagina(s) del PDF no contienen QR.".logAviso();
                var rutaArchivoAnalizando = archivoAnalizando.nombreCompletoConRuta();
                crearArchivosParaRevisarAviso(rutasArchivosSeparados[0], rutaArchivoAnalizando);
                rutasArchivosSeparados.remove(0);
            }

            for (int i = 0; i < rutasArchivosSeparados.longitud(); i++) {

                var ruta = rutasArchivosSeparados.get(i);
                String resultado;

                resultado = resultadosLecturaQRs.get(i);
                var resultadoLectura = new ResultadoLectura(ruta, resultado);
                resultadoFinal.add(resultadoLectura);

            }

        }else {
            ("No se han encontrado QRs en " + nombreDelArchivo).logError();
            var rutaArchivoAnalizando = archivoAnalizando.nombreCompletoConRuta();
            crearArchivoParaRevisarError(rutaArchivoAnalizando);
        }

        "Fin Analizando archivo.".logDebug();
        return resultadoFinal;

    }

    private HashMap<Integer, String> procesarPDFEnDisco(Pdf pdf) throws IOException {

        //Recuperamos la ruta en la cual renderizaremos las páginas del PDF:

        //Instancia del objeto que analizará cada página en busca de QRs:
        var lector = new LectorQR();

        //El método renderizarPDFEnDisco devuelve una lista de Strings con la ruta donde ha renderizado cada pagina:
        var paginas = pdf.renderizarPDFEnDisco(rutaCarpetaTemporal + "\\PNGsTemporales", new Coleccion());

        //Almacenamos el número de página que se va a analizar (la primera ha de ser 0).
        var paginaAnalizada = 0;

        //En este Diccionario almacenaremos el resultado final.
        // La llave será la página en la cual está el QR y el valor los contenidos del QR:
        var resultadoFinal = new HashMap<Integer, String>();

        "Analizando páginas".cabeceraDeLog().log();

        //Por cada imagen (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for (var pagina : paginas) {

            //Log de información sobre la página.
            ("Página " + (paginaAnalizada + 1) + "/" + paginas.longitud()).cabeceraDeLog('v').log();

//            if(paginaAnalizada + 1 == 14) {
//                "".log();
//            }

            //Detectamos QRs en la página.
            var resultado = lector.detectarQR(pagina);

            //Algunos QRs no valdrán así que los purgamos.
            var QRValido = purgarQRsInvalidos(resultado);

            //Si la instrucción anterior da resultados, almacenamos en la colección el resultado.
            if (!resultado.esNulo()) {
                resultadoFinal.asignar(paginaAnalizada, QRValido);
            }

            paginaAnalizada++;
        }

        "Fin Analizando páginas".cabeceraDeLog().log();

        return resultadoFinal;

    }

    private HashMap<Integer, String> procesarPDFEnMemoria(Pdf pdf) throws IOException {

        //Instancia del objeto que analizará cada página en busca de QRs:
        var lector = new LectorQR();

        //El método renderizarPDFEnMemoria devuelve una lista de BufferedImage con cada página:
        var paginas = pdf.renderizarPDFEnMemoria(new Coleccion());

        //Almacenamos el número de página que se va a analizar (la primera ha de ser 0).
        var paginaAnalizada = 0;

        //En esta colección almacenaremos el resultado final.
        // La llave será la página en la cual está el QR y el valor los contenidos del QR:
        var resultadoFinal = new HashMap<Integer, String>();

        "Analizando páginas".cabeceraDeLog().log();

        //Por cada BufferedImage (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for(var pagina : paginas) {

            //Log de información sobre la página.
            ("Página " + (paginaAnalizada + 1) + "/" + paginas.longitud()).cabeceraDeLog('v').log();

            //Convertir BufferedImage en InputStream:
            var os = new ByteArrayOutputStream();
            ImageIO.write(pagina, "png", os);
            var is = new ByteArrayInputStream(os.toByteArray());

            //Detectamos QRs en la página.
            var resultado = lector.detectarQR(is);

            //Algunos QRs no valdrán así que los purgamos.
            var QRValido = purgarQRsInvalidos(resultado);

            //Si la instrucción anterior da resultados, almacenamos el número de página.
            if (!resultado.esNulo()) {
                resultadoFinal.asignar(paginaAnalizada, QRValido);
            }

            paginaAnalizada++;

        }

        "Fin Analizando páginas".cabeceraDeLog().log();

        return resultadoFinal;

    }

    private void crearArchivoParaRevisarError(String rutaArchivoError) throws IOException {

        var archivoOrigen = new File(rutaArchivoError);
        var nombreArchivo = archivoOrigen.nombreConExtension();

        var rutaDestino = rutaCarpetaTemporal + "\\Revisar\\Error\\" + nombreArchivo;
        var archivoDestino = new File(rutaDestino);

        archivoOrigen.renameTo(archivoDestino);

        //Generar log:
        var rutaLog = archivoDestino.nombreCompletoConRuta().izquierdaDeUltimaOcurrencia(".") + "_LOG.txt";
        generarArchivoLog(rutaLog);

    }

    private void crearArchivosParaRevisarAviso(String rutaArchivoError, String rutaArchivoPadre) throws IOException {

        //Mover el archivo que ha fallado
        var archivoOrigen = new File(rutaArchivoError);
        var nombreArchivo = archivoOrigen.nombreConExtension();

        var rutaDestino = rutaCarpetaTemporal + "\\Revisar\\Aviso\\" + nombreArchivo;
        var archivoDestino = new File(rutaDestino);

        archivoOrigen.renameTo(archivoDestino);

        //Mover el archivo padre
        var archivoPadreOrigen = new File(rutaArchivoPadre);

        var rutaPadreDestino = rutaCarpetaTemporal + "\\Revisar\\Aviso\\" + archivoPadreOrigen.nombreConExtension();
        var archivoPadreDestino = new File(rutaPadreDestino);

        archivoPadreOrigen.renameTo(archivoPadreDestino);

        //Generar log.txt:
        var rutaLog = archivoDestino.nombreCompletoConRuta().izquierdaDeUltimaOcurrencia(".") + "_LOG.txt";
        generarArchivoLog(rutaLog);

    }

    private void generarArchivoLog(String ruta) throws IOException {
        var archivoLog = new File(ruta);

        var fileWriter = new FileWriter(archivoLog);
        fileWriter.write(procesarQRLog.logProceso);
        fileWriter.close();
    }

    private String purgarQRsInvalidos(List<String> contenidoQRs) {

        "Purgando QRs inválidos...".logDebug();
        var QRValido = "";

        var formatosAceptados = formatos.listaValor(x -> x.getValue());

        if(!contenidoQRs.esNulo()) {
            for(var qr : contenidoQRs) {

                var qrValidoEncontrado = false;

                for (var cabecera : formatosAceptados) {
                    if(qr.comienzaPor(cabecera)) {
                        QRValido = qr.aCadena();
                        qr.logDebug("QR valido");
                        qrValidoEncontrado = true;
                        break;
                    }else {
                        qr.logDebug("QR purgado");
                    }
                }

                if (qrValidoEncontrado) {
                    break;
                }

            }
        }

        "QRs purgados con éxito.".logDebug();

        return QRValido;

    }

    //Limpia los PNGs generados por el método procesarPDFEnDisco();
    private void eliminarPNGsIntermedios() {

        var carpetaTemporal = new File(rutaCarpetaTemporal + "\\PNGsTemporales");

        for (var f : carpetaTemporal.listFiles()) {
            if(f.extension().igual("png")) {
                f.delete();
            }
        }

    }

    private void leerConfiguracion(Coleccion conf) throws Exception {

        conf.aCadena().log("Parametros");

        if (conf.containsKey("carpetaTemporal")) {
            rutaCarpetaTemporal = conf.get("carpetaTemporal").aCadena();
        }else {
            throw new Exception("No se ha establecido carpeta temporal en la configuración");
        }

        if(conf.containsKey("extensiones")) {
            extensiones = conf.get("extensiones").aCadena().dividir(",");
        }else {
            extensiones = List.of("pdf");
        }

        if(conf.containsKey("tamañoMaxProcesarEnMemoriaMB")) {
            tamañoMaxProcesarEnMemoria = conf.get("tamañoMaxProcesarEnMemoriaMB").aInteger();
        }else {
            tamañoMaxProcesarEnMemoria = 5;
        }

        if(conf.containsKey("formatos")) {

            var formatosIntroducidos = conf.get("formatos").aCadena().dividir(",");
            formatosIntroducidos.listaValor(x -> formatos.asignar(x.izquierdaDeOcurrencia("="), x.derechaDeOcurrencia("=")));

        }else {
            //Por defecto estos serán los formatos aceptados.
            formatos = new Diccionario();
            formatos.asignar("ITB", "ITB");
            formatos.asignar("AVANBOX", "<T\tsc_add>");
        }


    }

    private class ProcesarQRLog implements RegistroLog {

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
