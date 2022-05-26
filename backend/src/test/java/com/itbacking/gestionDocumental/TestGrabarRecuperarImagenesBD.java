package com.itbacking.gestionDocumental;

import com.itbacking.core.Config;
import com.itbacking.core.collection.Coleccion;
import com.itbacking.core.model.Condicion;
import com.itbacking.core.model.OperadorCondicion;
import com.itbacking.db.connection.Conexion;
import com.itbacking.db.connection.ConfiguracionConexion;
import com.itbacking.db.connection.TipoConexion;
import com.itbacking.db.connector.ConectorDb;
import org.junit.Test;
import org.jvnet.hk2.annotations.Service;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

//@RunWith(App.class)
@SpringBootTest
@EnableScheduling
@EnableAsync
@Service
public class TestProyecto {

    private Conexion conexionGestionDocumental;
    private ConectorDb conectorDocumentos;
    @Test
    public void testGrabarByteArrayEnBD() throws Exception {

        asegurarConexion();

        var archivo = Config.leerRecurso("La cosa está que trina.png");

        var documentoByteArray = archivo.aByteArray();

        var documento = new Coleccion();
        documento.asignar("id", "prueba");
        documento.asignar("contenido", documentoByteArray);

        conectorDocumentos.ejecutarInsert(documento);

        var condiciones = List.of(new Condicion("id", "prueba", OperadorCondicion.IGUAL));

        var vuelta = conectorDocumentos.ejecutarSelect(null,condiciones);

        byte[] byteArrayVuelta = (byte[]) vuelta[0].get("contenido");

        var archivoDeSalida = new File("C:\\temp\\La Cosa Está Que Trina.png");

        var outputStream = new FileOutputStream(archivoDeSalida);
        outputStream.write(byteArrayVuelta);
        outputStream.close();

    }

    protected Conexion crearConexion() {

        //Obtener los parámetros de conexion de la variable parametrosConexion y establece la conexión.
        var tipo = "MySql";
        var servidor = "172.16.10.35";
        var bd = "GestionDocumental";
        var user = "itb";
        var password = "ITB%itb01";
        var puerto = "3306";

        var confConexion = new ConfiguracionConexion(TipoConexion.valueOf(tipo), servidor, bd, user, password, puerto.aInteger());
        return confConexion.crearConexion();

    }

    protected void asegurarConexion() {

        //Si no se ha establecido conexión todavía, establece una.
        if(conexionGestionDocumental != null) return;
        conexionGestionDocumental = crearConexion();

        //Obtenemos el nombre de las tablas necesarias:
        var repoDocumentos = "ContenidoDocumentos";

        //Se establece la conexión:
        conectorDocumentos=conexionGestionDocumental.obtenerConectorDb(repoDocumentos);

    }

}
