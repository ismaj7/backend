package com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces;

import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.ResultadosLectura;

import java.util.List;
import java.util.Map;

public interface ProcesarSincro {

    List<ResultadosLectura> procesarArchivos(Map<String, Object> fila) throws Exception;

}
