package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces.ProcesarResultados;

import java.util.List;
import java.util.Map;

public class ProcesarResultados_GestionDocumental implements ProcesarResultados {

    @Override
    public boolean procesarResultados(List<ResultadoLectura> resultados, Map<String, Object> fila) throws Exception {
        return false;
    }
}
