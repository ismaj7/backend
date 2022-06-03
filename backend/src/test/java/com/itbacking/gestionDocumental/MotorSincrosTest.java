package com.itbacking.gestionDocumental;
import com.itbacking.core.App;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.itb.gestionDocumental.MotorSincros.Clases.MotorSincros;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@EntityScan(
        basePackages = {"com.itbacking"},
        basePackageClasses = { MotorSincrosTest.class}
        )
@SpringBootApplication(exclude = MongoAutoConfiguration.class) //Necesario para evitar el Autowiring de Mongo que hace sin que se pida Autowiring
public class MotorSincrosTest {

    MotorSincros motorSincros;

    public static void main(String[] args) throws Exception {
        App.ejecutar(MotorSincrosTest.class, args);
//
//        var parametros = new Coleccion();
//
//        //Parametros para la conexión a BD.
//        parametros.asignar("tablaSincros", "SincronizacionesQR");
//        parametros.asignar("tablaLogs", "LogSincros");
//        parametros.asignar("tipoConexion", "MySql");                             //IllegalArgumentException
//        parametros.asignar("servidorConexion", "172.16.10.35");                  //SQLNonTransientConnectionException
//        parametros.asignar("bdConexion", "GestionDocumental");                   //SQLSyntaxErrorException
//        parametros.asignar("usuarioConexion", "itb");                            //SQLNonTransientConnectionException
//        parametros.asignar("passwordConexion", "ITB%itb01");                     //SQLNonTransientConnectionException
//        parametros.asignar("puertoConexion", "3306");                            //SQLNonTransientConnectionException
//
//        //Parametros para el envío de notificación por email.
//        parametros.asignar("usuario", "scara@itbacking.com");
//        parametros.asignar("password", "ITB%sc02");
//        parametros.asignar("destino", "ibahmane@itbacking.com");
//
//        var ms = new MotorSincros(parametros);
//
//
//        ms.procesarSincronizaciones();
    }

    @Scheduled(fixedDelay = 2000)
    public void procesarSincros() throws Exception {

        if (motorSincros == null) {
            var parametros = new Coleccion();
            //Parametros para la conexión a BD.
            parametros.asignar("tablaSincros", "SincronizacionesQR");
            parametros.asignar("tablaLogs", "LogSincros");
            parametros.asignar("tablaDocumentos", "Documentos");
            parametros.asignar("tablaEtiquetas", "Etiquetas");
            parametros.asignar("tipoConexion", "MySql");                             //IllegalArgumentException
            parametros.asignar("servidorConexion", "172.16.10.35");                  //SQLNonTransientConnectionException
            parametros.asignar("bdConexion", "GestionDocumental");                   //SQLSyntaxErrorException
            parametros.asignar("usuarioConexion", "itb");                            //SQLNonTransientConnectionException
            parametros.asignar("passwordConexion", "ITB%itb01");                     //SQLNonTransientConnectionException
            parametros.asignar("puertoConexion", "3306");                            //SQLNonTransientConnectionException

            //Parametros para el envío de notificación por email.
            parametros.asignar("usuario", "scara@itbacking.com");
            parametros.asignar("password", "ITB%sc02");
            parametros.asignar("destino", "ibahmane@itbacking.com");

            motorSincros = new MotorSincros(parametros);
        }

        motorSincros.procesarSincronizaciones();

    }

}


