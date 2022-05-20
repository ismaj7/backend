package com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces;

import com.itbacking.db.connector.ConectorDb;
import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.ResultadosLectura;

import java.util.List;
import java.util.Map;

public interface ProcesarResultados_Sincro {

    void procesarResultados(Map<String, Object> fila, List<ResultadosLectura> resultados) throws Exception;

}

