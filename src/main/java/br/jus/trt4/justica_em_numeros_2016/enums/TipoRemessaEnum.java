package br.jus.trt4.justica_em_numeros_2016.enums;

import java.util.Objects;

/**
 * Enum para mapear o tipo de uma remessa.
 *
 */
public enum TipoRemessaEnum {
    COMPLETA("C", "Completa"), MENSAL("M", "Mensal");
    
    private final String codigo;
    private final String label;
    
    private TipoRemessaEnum(String codigo, String label) {
        this.codigo = codigo;
        this.label = label;
    }
    
    /**
     * Método responsável por retornar a representação do enum como caracter
     * 
     * @return o caracter
     */
    public String getCodigo() {
        return this.codigo;
    }
    
    /**
     * Método responsável por retornar o enum associado ao código.
     * 
     * @param codigo codigo do enum
     * @return Retorna o tipo da remessa em {@link TipoRemessaEnum}.
     */
    public static TipoRemessaEnum criar(String codigo) {
        if (Objects.nonNull(codigo)) {
            for (TipoRemessaEnum tipoRemessa : TipoRemessaEnum.values()) {
                if (tipoRemessa.getCodigo().equals(codigo)) {
                    return tipoRemessa;
                }
            }
        }
        return null;
    }
    
    /**
     * Método responsável por retornar o enum associado ao label.
     * 
     * @param label label do enum
     * @return Retorna o tipo da remessa em {@link TipoRemessaEnum}.
     */
    public static TipoRemessaEnum criarApartirDoLabel(String label) {
        if (Objects.nonNull(label)) {
            for (TipoRemessaEnum tipoRemessa : TipoRemessaEnum.values()) {
                if (tipoRemessa.getLabel().equals(label)) {
                    return tipoRemessa;
                }
            }
        }
        return null;
    }
    
    public String getLabel() {
        return this.label;
    }
    
    @Override
    public String toString() {
        return getLabel();
    }
}

