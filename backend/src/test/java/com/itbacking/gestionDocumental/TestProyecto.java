package com.itbacking.gestionDocumental;

import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.itb.gestionDocumental.MotorSincros.MotorSincros;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(App.class)
@SpringBootTest
public class TestProyecto {

    @Test
    public void mainTest() throws Exception {

        var conexion = new Coleccion();
        conexion.asignar("tablaSincros", "SincronizacionesQR");
        conexion.asignar("tipoConexion", "MySql");                             //IllegalArgumentException
        conexion.asignar("servidorConexion", "172.16.10.35");                  //SQLNonTransientConnectionException
        conexion.asignar("bdConexion", "GestionDocumental");                   //SQLSyntaxErrorException
        conexion.asignar("usuarioConexion", "itb");                            //SQLNonTransientConnectionException
        conexion.asignar("passwordConexion", "ITB%itb01");                     //SQLNonTransientConnectionException
        conexion.asignar("puertoConexion", "3306");                            //SQLNonTransientConnectionException

        var motorSincros = new MotorSincros(conexion);
        motorSincros.iniciar();

    }

}
