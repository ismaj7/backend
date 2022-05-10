package com.itbacking.itb.gestionDocumental.MotorSincros;

import java.util.List;

public class ResultadosLectura {
    //Data class para el manejo de los resultados de la lectura de QRs.

    private String rutaArchivo;
    private List<String> valoresQR;

    public ResultadosLectura(String rutaArchivo, List<String> valorQRs) {

        this.rutaArchivo = rutaArchivo;
        this.valoresQR = valorQRs;

    }

    public List<String> getValoresQR() {
        return valoresQR;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }
}
