package com.itbacking.itb.gestionDocumental.MotorSincros.Clases;

import com.itbacking.core.collection.Diccionario;
import org.apache.commons.math3.geometry.spherical.oned.Arc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ArchivoXmlQR {

    public enum FormatoXML{Desconocido, Avanbox, ITB}
    public FormatoXML formato;
    private String mTextoQR;
    public static final String saltoDeLinea = "\r\n";
    private static Diccionario codigosQRITB = new Diccionario();
    public Map<String,String> valores = new Diccionario();
    public boolean error;
    public String descripcionError;

    private static boolean instanciado = false;

    //region Constructores:

    private ArchivoXmlQR() {

        if(!instanciado) {
            codigosQRITB.asignar("1", "ORIGEN");
            codigosQRITB.asignar("2", "CLAVE");
            codigosQRITB.asignar("3", "TIPO");
            codigosQRITB.asignar("4", "MODO");
            codigosQRITB.asignar("5", "ADICIONAL");
            codigosQRITB.asignar("6", "EXPEDIENTE1");
            codigosQRITB.asignar("7", "TIPOEXPEDIENTE1");
            codigosQRITB.asignar("8", "EXPEDIENTE2");
            codigosQRITB.asignar("9", "TIPOEXPEDIENTE2");
            codigosQRITB.asignar("A", "BD");

            instanciado = true;
        }

    }

    public ArchivoXmlQR(String pTextoQR) { //Para Avanbox

        this();
        this.formato = formatoDelQR(pTextoQR);
        if(this.formato == FormatoXML.Avanbox)
            parsearFormatoAvanbox(pTextoQR);
        else if(this.formato == FormatoXML.ITB)
            parsearFormatoITB(pTextoQR);

        mTextoQR = pTextoQR;
    }

    public ArchivoXmlQR(Map<String,String> valores) { //Para Avanbox
        this();
        this.formato = FormatoXML.Avanbox;
        this.valores=valores;
    }

    //endregion

    //region Traídos desde ServidorQR.cs:

    public static FormatoXML formatoDelQR(String pTextoQR) {
        if (pTextoQR.comienzaPor("<T\tsc_add>" + saltoDeLinea)) {
            return FormatoXML.Avanbox;
        } else if (pTextoQR.comienzaPor("ITB" + saltoDeLinea)) {
            return FormatoXML.ITB;
        }
        return FormatoXML.Desconocido;
    }

    public void parsearFormatoAvanbox(String pTexto) {
        var nLinNAV = pTexto.derechaDeOcurrencia("<S\tNAV\t").izquierdaDeOcurrencia(">" + saltoDeLinea);
        var nLinExp = pTexto.dividir(saltoDeLinea).listaCondicion(x->x.comienzaPor("<S\tEXPEDIENTE\t")).listaValor(x-> x.derechaDeOcurrencia("<S\tEXPEDIENTE\t").izquierdaDeOcurrencia(">"));
        var nValNAV = nLinNAV.dividir("\t");

        for(var nPar : nValNAV) {
            var nLstPar = nPar.dividir("=");
            if (nLstPar.longitud() == 2) {
                this.valores.asignar(nLstPar[0], nLstPar[1]);
            } else {
                this.descripcionError += (descripcionError != "" ? saltoDeLinea : "") + "Linea sin pares con texto: " + nPar;
                this.error = true;
            }
        }

        if(nLinExp.longitud()==2) {
            //2 expedientes
            var nLstPar = nLinExp.get(0).dividir("\t");
            this.valores.asignar("EXPEDIENTE2", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE2", nLstPar[1]);

            nLstPar = nLinExp.get(1).dividir("\t");
            this.valores.asignar("EXPEDIENTE1", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE1", nLstPar[1]);
        }
        else {
            var nLstPar = nLinExp.get(0).dividir("\t");
            this.valores.asignar("EXPEDIENTE1", nLstPar[0]);
            this.valores.asignar("TIPOEXPEDIENTE1", nLstPar[1]);
        }

    }

    public void parsearFormatoITB(String pTexto) {
        var nCadValores = pTexto.derechaDeOcurrencia("ITB" + saltoDeLinea);
        var nLstCampos = nCadValores.dividir(saltoDeLinea);
        for (var nPar : nLstCampos) {
            var nLstPar = nPar.dividir("\t");
            if(nLstPar.longitud()==2) {
                var nNombre = codigosQRITB[nLstPar[0]];
                if (nNombre != null)
                    valores.asignar(nNombre, nLstPar[1]);
                else
                {
                    descripcionError += (descripcionError != "" ? saltoDeLinea : "") + "Codigo de campo incorrecto: " + nPar;
                    error = true;
                }
            }
            else
            {
                descripcionError += (descripcionError!=""? saltoDeLinea:"") + "Linea sin pares con texto: " + nPar;
                error = true;
            }
        }
    }

    public String obtenerXMLAvanbox() throws Exception {
        if (formato == FormatoXML.Desconocido) {
            throw new Exception("No se puede generar el xml del formato desconocido");
        }

        String nRes = "<?xml version=" + "\"1.0\"" + " encoding=" + "\"ISO-8859-1\"" + "?>" + saltoDeLinea;
        nRes += "<avanbox>" + saltoDeLinea;
        nRes += "<type>schema_add</type>" + saltoDeLinea;

        if (valores.get("TIPOEXPEDIENTE2").aCadena()!="")
        {
            nRes += seccionExpedienteXML("schema_1", 2);
            nRes += seccionExpedienteXML("schema_3", 1);
        } else
            nRes += seccionExpedienteXML("schema_1", 1);

        nRes += "<schema_2>" + saltoDeLinea;
        nRes += "<alias>NAV</alias>" + saltoDeLinea;
        nRes += "<dossier_id />" + saltoDeLinea;
        nRes += "<dossier_type />" + saltoDeLinea;

        nRes += seccionItemXML(1, "ORIGEN", valores.get("ORIGEN"));
        nRes += seccionItemXML(2, "CLAVE", valores.get("CLAVE"));
        nRes += seccionItemXML(3, "TIPO", valores.get("TIPO"));
        nRes += seccionItemXML(4, "ADICIONAL", valores.get("ADICIONAL"));
        nRes += seccionItemXML(5, "BD", valores.get("BD"));

        nRes += "</schema_2>" + saltoDeLinea;
        nRes += "<autoclass>EXPEDIENTE</autoclass>" + saltoDeLinea;
        nRes += "</avanbox>";
        return nRes;
    }

    public String seccionExpedienteXML(String pSchema,int pNumExpediente) {

        //Operaciones para formatear la fecha:
        var formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var fecha = LocalDateTime.now();

        String nRes = "";
        nRes += "<" + pSchema +">" + saltoDeLinea;
        nRes += "<alias>EXPEDIENTE</alias>" + saltoDeLinea;
        nRes += "<dossier_id>" + valores.get("EXPEDIENTE" + pNumExpediente) + "</dossier_id>" + saltoDeLinea;
        nRes += "<dossier_type>" + valores.get("TIPOEXPEDIENTE" + pNumExpediente) + "</dossier_type>" + saltoDeLinea;
        if (pNumExpediente == 1)
        {
            nRes += "<item_1>" + saltoDeLinea;
            nRes += "<alias>FECHA</alias>" + saltoDeLinea;
            nRes += "<value>" + (formatoFecha.format(fecha)) + "</value>" + saltoDeLinea;
            nRes += "</item_1>" + saltoDeLinea;
        }
        nRes += "</" + pSchema + ">" + saltoDeLinea;
        return nRes;
    }

    public String seccionItemXML(int pNumero, String pAlias, String pValor) {
        String nRes = "";
        nRes += "<item_" + pNumero+ ">" + saltoDeLinea;
        nRes += "<alias>" + pAlias+ "</alias>" + saltoDeLinea;
        nRes += "<value>" + pValor + "</value>" + saltoDeLinea;
        nRes += "</item_" + pNumero+ ">" + saltoDeLinea;
        return nRes;
    }

    //endregion

}
