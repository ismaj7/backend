package com.itbacking.itb.gestionDocumental.MotorSincros;

import com.itbacking.code2d.LectorQR;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.collection.Lista;
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
        var conf = fila.get("confProcesarSincro").aCadena().aObjeto(Coleccion.class);
        leerConfiguracion(conf);

        //Creamos la carpeta temporal si no existe:
        crearCarpetaTemporal();

        //Buscamos los archivos para analizar:
        "Recopilando archivos a analizar...".logDebug().log();

        List<File> archivosParaAnalizar;

        //Primero los buscaremos en la carpeta temporal por si se ha quedado a medias la sincronización.
        archivosParaAnalizar = listarArchivosEnCarpeta(rutaCarpetaTemporal + "\\documentosAProcesar");
        var ruta = rutaCarpetaTemporal;//Esta cadena la usaremos para montar el log

        //Si en la carpeta temporal no había pdfs, nos vamos a la inicial
        if(archivosParaAnalizar.isEmpty()) {
            archivosParaAnalizar = listarArchivosEnCarpeta(rutaCarpetaInicial);
            ruta = rutaCarpetaInicial;
        }

        //Contamos los archivos para analizar porque si no hay archivos o no tienen las extensiones correctas no analizaremos nada.
        var numArchivosParaAnalizar = contarArchivos(archivosParaAnalizar);
        (numArchivosParaAnalizar + " archivos para analizar encontrados en \"" + ruta + "\".").log();

        //Entramos al análisis si hay archivos.
        if (numArchivosParaAnalizar > 0) {

            "Analizando archivos...".cabeceraDeLog().log();

            //Analizamos los archivos:
            return analizarArchivos(archivosParaAnalizar);
        }else {
            return new ArrayList<ResultadosLectura>();
        }

    }

    private Integer contarArchivos(List<File> archivosParaAnalizar) {

        var cuenta = 0;

        for (var archivo : archivosParaAnalizar) {
            var extension = archivo.extension();
            if (extensiones.contains(extension)) {
                cuenta++;
            }
        }

        return cuenta;
    }

    private List<ResultadosLectura> analizarArchivos(List<File> archivos) throws IOException {

        var resultadoFinal = new ArrayList<ResultadosLectura>();

        for(File f : archivos) {

            //Movemos el archivo a nuestra carpeta.
            var archivo = new File(rutaCarpetaTemporal + "\\documentosAProcesar\\" + f.nombreConExtension());
            f.renameTo(archivo);
            //Extraemos del archivo la info que necesitamos:
            var nombreDelArchivo = archivo.nombreConExtension();
            var tamañoArchivo = archivo.length()/1000000; // dividimos entre 1000000 para pasarlo a MB

            //Creamos la instancia de la clase Pdf con la ruta del archivo:
            var pdf = new Pdf(archivo.nombreCompletoConRuta());

            //En esta colección guardaremos el resultado de procesarPDF
            //LLave: Número de página - Valor: String(s) con el resultado de leer el QR
            var resultadoProcesarPDF = new HashMap<Integer, String>();

            //Si el tamaño es mayor del que pone en la BD, se hará en disco y si no en memoria:
            if (tamañoArchivo > tamañoMaxProcesarEnMemoria) {
                ("Analizando " + archivo.nombreConExtension() + " en disco" + " (" + tamañoArchivo +" MB)...").logDebug().log();
                resultadoProcesarPDF = procesarPDFEnDisco(pdf);
                eliminarPNGsIntermedios();
                "Hecho".log();
            }else {
                ("Analizando " + archivo.nombreConExtension() + " en memoria" + " (" + tamañoArchivo +" MB)...").logDebug().log();
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

                for (int i = 0; i < rutasArchivosSeparados.longitud(); i++) {

                    var ruta = rutasArchivosSeparados.get(i);
                    String resultado;

                    try {
                        resultado = resultadosLecturaQRs.get(i);
                        var resultadoLectura = new ResultadosLectura(ruta, resultado);
                        resultadoFinal.add(resultadoLectura);
                    }catch(IndexOutOfBoundsException ioobe) {
                        resultado = "";
                        var resultadoLectura = new ResultadosLectura(ruta, resultado);
                        resultadoFinal.add(resultadoLectura);
                    }

                }

            }else {
                ("No se han encontrado QRs en " + nombreDelArchivo).log();
            }

        }

        "Fin Analizando archivos.".logDebug();
        return resultadoFinal;

    }

    private List<File> listarArchivosEnCarpeta(String rutaCarpeta) throws FileNotFoundException {

        //Instanciamos la lista que retornaremos al final.
        var archivos = new ArrayList<File>();

        var carpeta = new File(rutaCarpeta);

        //Comprobamos que la carpeta exista o no
        if(!carpeta.exists()) {
            "No se puede encontrar la carpeta introducida.".logError(carpeta.nombreCompletoConRuta());
            "Fin servicio".cabeceraDeLog().log();
            throw new FileNotFoundException();
        }else {

            //Recorremos los archivos de la carpeta y los añadimos a la lista que retornaremos al final.
            for(File f : carpeta.listFiles()) {
                if(extensiones.contains(f.extension())) {
                    archivos.add(f);
                }
            }

        }

        return archivos;
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

        //Por cada imagen (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for (var pagina : paginas) {

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

        //Por cada BufferedImage (correspondiente a una pag del pdf) ejecutamos detectarQR():
        for(var pagina : paginas) {

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

        return resultadoFinal;

    }

    private String purgarQRsInvalidos(List<String> contenidoQRs) {

        //Solo nos interesa el primer QR cuyo contenido comienza por <T sc_add> o ITB para luego montar el XML.

        "Purgando QRs inválidos...".logDebug();
        var QRValido = "";

        if(!contenidoQRs.esNulo()) {
            for(var qr : contenidoQRs) {
                if(qr.comienzaPor("<T\tsc_add>") || qr.comienzaPor("ITB")) {
                    QRValido = qr.aCadena();
                    break;
                }
            }
        }

        return QRValido;

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
        }else {

            //Creamos las carpetas necesarias dentro de la ruta temporal.

            var pngTemporales = new File(rutaCarpetaTemporal + "\\PNGsTemporales");
            pngTemporales.mkdir();

            var documentosAProcesar = new File(rutaCarpetaTemporal + "\\documentosAProcesar");
            documentosAProcesar.mkdir();

            var paginasSeparadas = new File(rutaCarpetaTemporal + "\\paginasSeparadas");
            paginasSeparadas.mkdir();

        }

        "Carpeta temporal creada con éxito".logDebug().log();
        return rutaCarpetaTemporal;

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


    }

}
