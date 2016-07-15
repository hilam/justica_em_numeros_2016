//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.07.14 at 01:46:33 PM BRT 
//


package br.jus.cnj.intercomunicacao_2_2;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for tipoQualificacaoPessoa.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="tipoQualificacaoPessoa">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="fisica"/>
 *     &lt;enumeration value="juridica"/>
 *     &lt;enumeration value="autoridade"/>
 *     &lt;enumeration value="orgaorepresentacao"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "tipoQualificacaoPessoa")
@XmlEnum
public enum TipoQualificacaoPessoa {

    @XmlEnumValue("fisica")
    FISICA("fisica"),
    @XmlEnumValue("juridica")
    JURIDICA("juridica"),
    @XmlEnumValue("autoridade")
    AUTORIDADE("autoridade"),
    @XmlEnumValue("orgaorepresentacao")
    ORGAOREPRESENTACAO("orgaorepresentacao");
    private final String value;

    TipoQualificacaoPessoa(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TipoQualificacaoPessoa fromValue(String v) {
        for (TipoQualificacaoPessoa c: TipoQualificacaoPessoa.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
