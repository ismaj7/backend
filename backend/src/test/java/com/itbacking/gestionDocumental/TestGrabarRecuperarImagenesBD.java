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

import java.io.*;
import java.util.List;

//@RunWith(App.class)
@SpringBootTest
@EnableScheduling
@EnableAsync
@Service
public class TestGrabarRecuperarImagenesBD {

    private Conexion conexionGestionDocumental;
    private ConectorDb conectorDocumentos;
    @Test
    public void testGrabarByteArrayEnBD() throws Exception {

        asegurarConexion();

        var archivo = Config.leerRecurso("Imagen1.png");

        var documentoByteArray = archivo.aByteArray();

        var documento = new Coleccion();
        documento.asignar("id", "prueba1");
        documento.asignar("contenido", documentoByteArray);

        conectorDocumentos.ejecutarInsert(documento);

        var condiciones = List.of(new Condicion("id", "prueba1", OperadorCondicion.IGUAL));

        var resultadoSQL = conectorDocumentos.ejecutarSelect(null,condiciones);

        byte[] byteArrayVuelta = objetoAByteArray(resultadoSQL[0].get("contenido"));

        var archivoDeSalida = new File("C:\\temp\\Imagen1_Leída.png");

        var outputStream = new FileOutputStream(archivoDeSalida);
        outputStream.write(byteArrayVuelta);
        outputStream.close();

    }

    public byte[] objetoAByteArray(Object objeto) throws IOException {

        return (byte[]) objeto;

        // No consigo hacer esto funcionar
//        var byteArrayOutputStream = new ByteArrayOutputStream();
//        var objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
//        objectOutputStream.writeObject(objeto);
//        objectOutputStream.flush();
//
//        return byteArrayOutputStream.toByteArray();

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
