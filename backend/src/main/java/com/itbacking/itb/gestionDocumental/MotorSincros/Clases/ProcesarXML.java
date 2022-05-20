package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarSincro;

import java.util.List;
import java.util.Map;

public class ProcesarXML implements ProcesarSincro {

    Coleccion parametros;

    public ProcesarXML() {
    }

    @Override
    public List<ResultadosLectura> procesarArchivos(Map<String, Object> fila) {
        return null;
    }
}
