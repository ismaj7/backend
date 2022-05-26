package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import java.util.Map;

public class ResultadoLectura {
    //Data class para el manejo de los resultados de la lectura de QRs.

    private String rutaArchivo;
    private String valorQR;
    public Map<String,String> etiquetas;

    public ResultadoLectura(String rutaArchivo, String valorQRs) {

        this.rutaArchivo = rutaArchivo;
        this.valorQR = valorQRs;
        etiquetas = new ArchivoXmlQR(this.valorQR).valores;
    }


    public String getRutaArchivo() {
        return rutaArchivo;
    }
}
