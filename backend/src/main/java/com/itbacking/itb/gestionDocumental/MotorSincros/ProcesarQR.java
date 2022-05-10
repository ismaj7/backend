package com.itbacking.itb.gestionDocumental.MotorSincros;

import com.itbacking.code2d.LectorQR;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.pdf.Pdf;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcesarQR implements ProcesarSincro {

    private String rutaCarpetaInicial;
    private String rutaCarpetaTemporal;
    private List<String> extensiones;
    private Integer tamañoMaxProcesarEnMemoria;

    @Override
    public List<ResultadosLectura> procesarArchivos(Map<String, Object> fila) throws Exception {

        //De la fila que nos llegan cogemos solo los parametros que nos interesan:
        rutaCarpetaInicial = fila.get("carpetaOrigen").aCadena();
        //var conf = fila.get("confProcesado").aCadena().dividir(",");
        var conf = fila.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        leerConfiguracion(conf);

        //Buscamos en la carpeta incial los archivos a analizar:
        "Recopilando archivos a analizar...".logDebug().log();
        var archivosParaAnalizar = listarArchivosCarpetaInicial();
        var numArchivosParaAnalizar = archivosParaAnalizar.longitud();
        (numArchivosParaAnalizar + " archivos para analizar encontrados.").log();

        //Analizamos los archivos:
        "Analizando archivos de la carpeta inicial...".logDebug().log();
        return analizarArchivosCarpetaInicial(archivosParaAnalizar);


    }

    private List<ResultadosLectura> analizarArchivosCarpetaInicial(List<File> archivos) throws IOException {

        var resultadoFinal = new ArrayList<ResultadosLectura>();

        for(File f : archivos) {

            //Extraemos del archivo la info que necesitamos:
            var extensionArchivo = f.extension();
            var nombreDelArchivo = f.nombreConExtension();
            var tamañoArchivo = f.length()/1000000; // dividimos entre 1000000 para pasarlo a MB

            if(extensiones.contains(extensionArchivo)) {

                //Creamos la instancia de la clase Pdf con la ruta del archivo:
                var pdf = new Pdf(f.nombreCompletoConRuta());

                //En esta colección guardaremos el resultado de procesarPDF
                //LLave: Número de página - Valor: String(s) con el resultado de leer el QR
                var resultadoProcesarPDF = new HashMap<Integer, List<String>>();

                //Si el tamaño es mayor del que pone en la BD, se hará en disco y si no en memoria:
                if (tamañoArchivo > tamañoMaxProcesarEnMemoria) {
                    ("Analizando " + f.nombreConExtension() + " en disco" + " (" + tamañoArchivo +" MB)...").logDebug().log();
                    resultadoProcesarPDF = procesarPDFEnDisco(pdf);
                    limpiarCarpetaTemporal();
                    //"Hecho".logDebug().log();
                }else {
                    ("Analizando " + f.nombreConExtension() + " en memoria" + " (" + tamañoArchivo +" MB)...").logDebug().log();
                    resultadoProcesarPDF = procesarPDFEnMemoria(pdf);
                    //"Hecho".logDebug().log();
                }

                //Almacenaremos aquí el número de las páginas del PDF que contengan un QR y los resultados de las lecturas:
                var paginasQueContienenQR = resultadoProcesarPDF.listaValor(x -> x.getKey());
                var resultadosLecturaQRs = resultadoProcesarPDF.listaValor(x -> x.getValue());

                //Si paginasQueContienenQR está vacío significa que no se han encontrado QRs en el documento.
                if (!paginasQueContienenQR.isEmpty()) {
                    //Dividimos el PDF original en diversos PDFs con las páginas que contienenQR
                    var rutasArchivosSeparados = pdf.dividirPDFPorNumerosDePagina(paginasQueContienenQR, rutaCarpetaTemporal);

                    for (int i = 0; i < rutasArchivosSeparados.longitud(); i++) {

                        var ruta = rutasArchivosSeparados.get(i);
                        List<String> resultado;

                        try {
                            resultado = resultadosLecturaQRs.get(i);
                            var resultadoLectura = new ResultadosLectura(ruta, resultado);
                            resultadoFinal.add(resultadoLectura);
                        }catch(IndexOutOfBoundsException ioobe) {
                            resultado = new ArrayList<String>();
                            resultado.add("");
                            var resultadoLectura = new ResultadosLectura(ruta, resultado);
                            resultadoFinal.add(resultadoLectura);
                        }

                    }

                }else {
                    ("No se han encontrado QRs en " + nombreDelArchivo).log();
                }

            }else {
                if (!f.isDirectory())
                    "Extensión no aceptada".logDebug(f.nombreConExtension());
            }

        }

        return resultadoFinal;

    }

    private List<File> listarArchivosCarpetaInicial() throws FileNotFoundException {

        //Instanciamos la lista que retornaremos al final.
        var archivos = new ArrayList<File>();

        var carpetaInicial = new File(rutaCarpetaInicial);

        //Comprobamos que la carpeta exista o no
        if(!carpetaInicial.exists()) {
            "No se puede encontrar la carpeta introducida.".logError(carpetaInicial.nombreCompletoConRuta());
            "Fin servicio".cabeceraDeLog().log();
            throw new FileNotFoundException();
        }else {

            //Recorremos los archivos de la carpeta y los añadimos a la lista que retornaremos al final.
            for(File f : carpetaInicial.listFiles()) {
                archivos.add(f);
            }

        }

        return archivos;
    }

    private HashMap<Integer, List<String>> procesarPDFEnDisco(Pdf pdf) throws IOException {

        //Recuperamos la ruta en la cual renderizaremos las páginas del PDF:

        //Instancia del objeto que analizará cada página en busca de QRs:
        var lector = new LectorQR();

        //El método renderizarPDFEnDisco devuelve una lista de Strings con la ruta donde ha renderizado cada pagina:
        var paginas = pdf.renderizarPDFEnDisco(rutaCarpetaTemporal, new Coleccion());

        //Almacenamos el número de página que se va a analizar (la primera ha de ser 0).
        var paginaAnalizada = 0;

        //En este Diccionario almacenaremos el resultado final.
        // La llave será la página en la cual está el QR y el valor los contenidos del QR:
        var resultadoFinal = new HashMap<Integer, List<String>>();

        //Por cada imagen (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for (var pagina : paginas) {

            //Detectamos QRs en la página.
            var resultado = lector.detectarQR(pagina);

            //Algunos QRs no valdrán así que los purgamos.
            resultado = purgarQRsInvalidos(resultado);

            //Si la instrucción anterior da resultados, almacenamos en la colección el resultado.
            if (!resultado.isEmpty()) {

                //TODO comprobar QR(s) y añadirlo(s) solo si es válido.
                resultadoFinal.asignar(paginaAnalizada, resultado);
            }

            paginaAnalizada++;
        }

        return resultadoFinal;

    }

    private HashMap<Integer, List<String>> procesarPDFEnMemoria(Pdf pdf) throws IOException {

        //Instancia del objeto que analizará cada página en busca de QRs:
        var lector = new LectorQR();

        //El método renderizarPDFEnMemoria devuelve una lista de BufferedImage con cada página:
        var paginas = pdf.renderizarPDFEnMemoria(new Coleccion());

        //Almacenamos el número de página que se va a analizar (la primera ha de ser 0).
        var paginaAnalizada = 0;

        //En esta colección almacenaremos el resultado final.
        // La llave será la página en la cual está el QR y el valor los contenidos del QR:
        var resultadoFinal = new HashMap<Integer, List<String>>();

        //Por cada BufferedImage (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for(var pagina : paginas) {

            //Convertir BufferedImage en InputStream:
            var os = new ByteArrayOutputStream();
            ImageIO.write(pagina, "png", os);
            var is = new ByteArrayInputStream(os.toByteArray());

            //Detectamos QRs en la página.
            var resultado = lector.detectarQR(is);

            //Algunos QRs no valdrán así que los purgamos.
            resultado = purgarQRsInvalidos(resultado);

            //Si la instrucción anterior da resultados, almacenamos el número de página.
            if (!resultado.isEmpty()) {
                resultadoFinal.asignar(paginaAnalizada, resultado);
            }

            paginaAnalizada++;

        }

        return resultadoFinal;

    }

    private List<String> purgarQRsInvalidos(List<String> contenidoQRs) {

        //Solo nos interesan los QRs cuyo contenido comienza por <T sc_add> o ITB para luego montar el XML.

        "Purgando QRs inválidos...".logDebug();
        var listaFinal = new ArrayList<String>();

        if(!contenidoQRs.esNulo()) {
            for(var qr : contenidoQRs) {
                if(qr.comienzaPor("<T\tsc_add>") || qr.comienzaPor("ITB")) {
                    listaFinal.add(qr);
                }
            }
        }

        return listaFinal;

    }

    private String crearCarpetaTemporal() throws Exception {

        //C:\temp\pruebas\ResultadosTemporales

        "Creando carpeta temporal...".logDebug().log();

        var carpetaTemporal = new File(rutaCarpetaTemporal);

        if(!carpetaTemporal.exists()) {
            carpetaTemporal.mkdir();
        }

        //Checkeamos que la carpeta sea directorio y si no lo es lanzamos una excepción para que el usuario revise la configuración.
        if(!carpetaTemporal.isDirectory()) {
            "Comprueba la configuración introducida: ".logError();
            throw new Exception("No se puede establecer la carpeta temporal en la siguiente ruta: \"" + carpetaTemporal + "\"");
        }

        "Carpeta temporal creada con éxito".logDebug().log();
        return rutaCarpetaTemporal;

    }

    //Limpia los PNGs generados por el método procesarPDFEnDisco();
    private void limpiarCarpetaTemporal() {

        var carpetaTemporal = new File(rutaCarpetaTemporal);

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
            crearCarpetaTemporal();
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


    }

}
