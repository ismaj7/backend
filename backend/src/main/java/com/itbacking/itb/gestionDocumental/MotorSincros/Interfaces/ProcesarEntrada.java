package com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces;

import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.ResultadoLectura;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface ProcesarEntrada {
    List<ResultadoLectura> procesarDocumentoEntrada(File archivo, Map<String, Object> sincronizacion) throws Exception;

}
