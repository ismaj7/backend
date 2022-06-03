package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.Sistema;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSalida;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProcesarSalida_GestionDocumental implements ProcesarSalida {

    private ConectorDb conectorDocumentos;
    private ConectorDb conectorEtiquetas;

    //Recibirá el conector sobre el que ejecutará los inserts/selects
    public ProcesarSalida_GestionDocumental(ConectorDb conectorDocumentos, ConectorDb conectorEtiquetas) {
        this.conectorDocumentos = conectorDocumentos;
        this.conectorEtiquetas = conectorEtiquetas;
    }

    @Override
    public boolean procesarSalida(List<ResultadoLectura> resultados, Map<String, Object> sincronizacion) throws Exception {

        try {
            //Registramos los resultados asociados al documento original en BD.
            registrarResultadosEnBD(resultados);
            return true;
        }catch (Exception e) {
            return false;
        }

    }

    private void registrarResultadosEnBD(List<ResultadoLectura> resultados) throws Exception {

        for(var resultado : resultados) {

            var documento = new File(resultado.obtenerRutaArchivo());

            var documentoInputStream = new FileInputStream(documento);
            var nombreDocumento = documento.nombreConExtension();
            var etiquetas = resultado.etiquetas;

            archivarDocumento(documentoInputStream, nombreDocumento, etiquetas);

            documentoInputStream.close();
        }

    }

    private void archivarDocumento(InputStream documento , String nombreDocumento, Map<String,String> etiquetas) throws Exception {

        var guidOperacion = Sistema.crearGUID();

        var documentoByteArray = documento.aByteArray();

        var registrosBD = new Coleccion();
        registrosBD.asignar("guid", guidOperacion);
        registrosBD.asignar("nombreDocumento", nombreDocumento);
        registrosBD.asignar("contenido", documentoByteArray);

        conectorDocumentos.ejecutarInsert(registrosBD);

        registrarEtiquetas(etiquetas, guidOperacion);

    }

    private void registrarEtiquetas(Map<String,String> etiquetas, UUID guidOperacion) throws Exception {

        for (var etiqueta : etiquetas.aLista()) {
            var etiquetaBD = new Coleccion();

            etiquetaBD.asignar("id", guidOperacion);
            etiquetaBD.asignar("codigo", etiqueta.getKey());
            etiquetaBD.asignar("valor", etiqueta.getValue());

            conectorEtiquetas.ejecutarInsert(etiquetaBD);
        }

    }

}

