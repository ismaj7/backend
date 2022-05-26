package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSincro;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ProcesarXML implements ProcesarSincro {

    Coleccion parametros;

    public ProcesarXML() {
    }

    @Override
    public List<ResultadoLectura> analizarDocumento(File archivo, Map<String, Object> fila) throws Exception {
        return null;
    }
}
