package com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces;

import com.itbacking.core.collection.Coleccion;
import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.ResultadoLectura;

import java.util.List;
import java.util.Map;

public interface ProcesarResultados_Sincro {

    boolean procesarResultados(List<ResultadoLectura> resultados, Map<String, Object> fila) throws Exception;

}

