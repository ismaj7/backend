package com.itbacking.itb.gestionDocumental.MotorSincros;

import java.util.List;
import java.util.Map;

public interface ProcesarResultados {

    void procesarResultados(Map<String, Object> fila, List<ResultadosLectura> resultados) throws Exception;

}

