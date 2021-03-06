package br.jus.trt4.justica_em_numeros_2016.enums;

/**
 * Lista todos os parâmetros que podem ser lidos do arquivo "config.properties".
 * 
 * Foi criado um "enum" para facilitar a identificação de parâmetros inválidos no
 * arquivo de configurações e também para localizar atributos depreciados.
 * 
 * @author fgiotto
 */
public enum Parametro {
	gerar_xml_1G,
	gerar_xml_2G,
	url_jdbc_1g,
	url_jdbc_2g,
	url_webservice_cnj,
	assunto_padrao_1G,
	assunto_padrao_2G,
	arquivo_serventias_cnj,
	pasta_saida_padrao,
	tamanho_lote_geracao_processos_operacao_2,
	numero_threads_simultaneas_operacao_2,
	numero_threads_simultaneas_operacao_3,
	situacao_xml_para_envio_operacao_3,
	tamanho_lote_envio_operacao_3,
	tipo_carga_xml,
	mes_ano_corte,
	codigo_municipio_ibge_trt,
	url_jdbc_egestao_1g,
	url_jdbc_egestao_2g,
	proxy_host,
	proxy_port,
	proxy_username,
	proxy_password,
	sigla_tribunal,
	password_tribunal,
	contornar_falta_de_genero,
	interface_grafica_fechar_automaticamente,
	url_validador_cnj,
	debug_gravar_relatorio_validador_cnj,
	url_legado_1g,
	url_legado_2g,
	sistema_judicial,
	descartar_movimentos_pje_ausentes_de_para_cnj,
	mesclar_movimentos_legado_migrado_via_xml,
	movimentos_sem_serventia_cnj,
	incluir_codigo_complemento_tipos_identificador_e_livre,
	possui_deslocamento_oj_legado_1g,
	possui_deslocamento_oj_legado_2g,
	tipo_validacao_protocolo_cnj,
	tamanho_lote_commit_operacao_1,
	tamanho_lote_commit_xml_legado,
	tamanho_batch
	;
}
