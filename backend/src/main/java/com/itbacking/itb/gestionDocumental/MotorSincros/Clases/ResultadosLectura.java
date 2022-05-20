package com.itbacking.itb.gestionDocumental.MotorSincros;

import java.util.List;

public class ResultadosLectura {
    //Data class para el manejo de los resultados de la lectura de QRs.

    private String rutaArchivo;
    private String valorQR;

    public ResultadosLectura(String rutaArchivo, String valorQRs) {

        this.rutaArchivo = rutaArchivo;
        this.valorQR = valorQRs;

    }

    public String getValorQR() {
        return valorQR;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }
}
