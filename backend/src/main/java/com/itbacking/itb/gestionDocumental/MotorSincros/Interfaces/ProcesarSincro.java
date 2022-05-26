package com.itbacking.itb.gestionDocumental.MotorSincros.Interfaces;

import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.ResultadoLectura;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface ProcesarSincro {

    List<ResultadoLectura> analizarDocumento(File archivo, Map<String, Object> fila) throws Exception;

}
