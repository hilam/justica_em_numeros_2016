package br.jus.trt4.justica_em_numeros_2016.tasks;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import br.jus.trt4.justica_em_numeros_2016.auxiliar.AcumuladorExceptions;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.Auxiliar;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.HttpUtil;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.MetaInformacaoEnvio;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.ProgressoInterfaceGrafica;
import br.jus.trt4.justica_em_numeros_2016.dao.JPAUtil;
import br.jus.trt4.justica_em_numeros_2016.dao.LoteDao;
import br.jus.trt4.justica_em_numeros_2016.dao.LoteProcessoDao;
import br.jus.trt4.justica_em_numeros_2016.dao.RemessaDao;
import br.jus.trt4.justica_em_numeros_2016.entidades.Lote;
import br.jus.trt4.justica_em_numeros_2016.entidades.LoteProcesso;
import br.jus.trt4.justica_em_numeros_2016.entidades.Remessa;
import br.jus.trt4.justica_em_numeros_2016.enums.Parametro;
import br.jus.trt4.justica_em_numeros_2016.enums.SituacaoLoteEnum;
import br.jus.trt4.justica_em_numeros_2016.enums.SituacaoLoteProcessoEnum;
import br.jus.trt4.justica_em_numeros_2016.enums.SituacaoXMLParaEnvioEnum;
import br.jus.trt4.justica_em_numeros_2016.enums.TipoRemessaEnum;
import br.jus.trt4.justica_em_numeros_2016.enums.TipoValidacaoProtocoloCNJEnum;
import br.jus.trt4.justica_em_numeros_2016.util.DataJudUtil;

/**
 * Chama os webservices do CNJ, validando cada um dos protocolos recebidos previamente do CNJ na fase 3:
 * {@link Op_3_EnviaArquivosCNJ}.
 * 
 * TODO: Implementar também o reenvio automático, se for erro interno no CNJ, dos processos dos protocolos com erro.
 * Como sugestão criar um arquivo do tipo .protocolo.erro que terá todos os protocolos com status de erro de um
 * processo.
 *
 * @author felipe.giotto@trt4.jus.br
 */
public class Op_4_ConfereProtocolosCNJ {

	private static final Logger LOGGER = LogManager.getLogger(Op_4_ConfereProtocolosCNJ.class);
	private CloseableHttpClient httpClient;
	private static ProgressoInterfaceGrafica progresso;

	private static final String NOME_PARAMETRO_PROTOCOLO = "protocolo";
	private static final String NOME_PARAMETRO_DATA_INICIO = "dataInicio";
	private static final String NOME_PARAMETRO_DATA_FIM = "dataFim";
	private static final String NOME_PARAMETRO_STATUS = "status";
	private static final String NOME_PARAMETRO_PAGINA = "page";

	private static int qtdProtocolosConferidos = 0;

	private TipoValidacaoProtocoloCNJEnum tipoValidacaoProtocoloCNJ;
	private boolean considerarXMLsComErro;

	// Padrão de como a última página consultada no CNJ de um status é armazenada no
	// arquivo. Composto pelo número do status (que é preenchido dinamicamente), espaço e o
	// número da página (dígito com 1 a 10 números).
	private static final String PADRAO_ER_ARQUIVO_ULTIMA_PAGINA_CONSULTADA = "^(%s )(\\d{1,10})$";

	private static final RemessaDao remessaDAO = new RemessaDao();
	private static final LoteProcessoDao loteProcessoDAO = new LoteProcessoDao();
	private static final LoteDao loteDAO = new LoteDao();

	public static void main(String[] args) throws Exception {

		String resposta;

		if (args != null && args.length > 0) {
			resposta = args[0];
		} else {
			System.out.println(
					"Se algum arquivo ainda não foi processado no CNJ, ou se ocorrer algum erro na resposta do CNJ, você quer que a operação seja reiniciada?");
			System.out.println(
					"Responda 'S' para que as validações no CNJ rodem diversas vezes, até que o webservice não recuse nenhum arquivo e até que todos os XMLs sejam processados.");
			System.out.println("Responda 'N' para que o envio ao CNJ rode somente uma vez.");
			resposta = Auxiliar.readStdin().toUpperCase();
		}

		executarOperacaoConfereProtocolosCNJ("S".equals(resposta));
	}

	/**
	 * Prepara o componente HttpClient para conectar aos serviços REST do CNJ
	 * 
	 */
	public Op_4_ConfereProtocolosCNJ() {
		this.httpClient = HttpUtil.criarNovoHTTPClientComAutenticacaoCNJ();
		
		String tipoValidacao = Auxiliar.getParametroConfiguracao(Parametro.tipo_validacao_protocolo_cnj,
				false);

		if (this.isTipoValidacaoProtocoloCNJValido(tipoValidacao)) {
			if (this.tipoValidacaoProtocoloCNJ != null) {
				this.tipoValidacaoProtocoloCNJ = TipoValidacaoProtocoloCNJEnum.criar(tipoValidacao);
				LOGGER.info(String.format("Tipo de validação escolhido: %s.", this.tipoValidacaoProtocoloCNJ.getCodigo()));
			} else {
				// Validação padrão quando nenhum valor é preenchido
				this.tipoValidacaoProtocoloCNJ = TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS;
				LOGGER.info("Tipo de validação escolhido: " + this.tipoValidacaoProtocoloCNJ.getCodigo());
			}
		} else {
			throw new RuntimeException("Valor desconhecido para o parâmetro 'tipo_validacao_protocolo_cnj': "
					+ tipoValidacao);
		}

		this.considerarXMLsComErro = false;
		String situacaoXMLParaEnvio = Auxiliar.getParametroConfiguracao(Parametro.situacao_xml_para_envio_operacao_3,
				false);
		if (situacaoXMLParaEnvio == null || SituacaoXMLParaEnvioEnum.ENVIAR_TODOS_OS_XMLS.getCodigo().equals(situacaoXMLParaEnvio)) {
			this.considerarXMLsComErro = true;
		} else if (SituacaoXMLParaEnvioEnum.ENVIAR_APENAS_XMLS_GERADOS_COM_SUCESSO.getCodigo().equals(situacaoXMLParaEnvio)) {
			this.considerarXMLsComErro = false;
		} else {
			throw new RuntimeException("Valor desconhecido para o parâmetro 'situacao_xml_para_envio_operacao_3': "
					+ situacaoXMLParaEnvio);
		}
	}

	private boolean isTipoValidacaoProtocoloCNJValido(String tipoValidacao) {
		return tipoValidacao == null
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS_COM_ERRO.getCodigo().equals(tipoValidacao)
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_PROCESSADO_COM_ERRO.getCodigo().equals(tipoValidacao)
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_NO_ARQUIVO.getCodigo().equals(tipoValidacao)
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_GRAVACAO.getCodigo().equals(tipoValidacao)
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS.getCodigo().equals(tipoValidacao);
	}

	public static void executarOperacaoConfereProtocolosCNJ(boolean continuarEmCasoDeErro) throws Exception {
		Auxiliar.prepararPastaDeSaida();

		if (continuarEmCasoDeErro) {
			LOGGER.info("Se ocorrer algum erro no envio, a operação será reiniciada quantas vezes for necessário!");
		}

		progresso = new ProgressoInterfaceGrafica("(4/5) Conferência dos protocolos no CNJ");
		try {
			boolean executar = true;

			do {
				progresso.setProgress(0);

				Op_4_ConfereProtocolosCNJ operacao = new Op_4_ConfereProtocolosCNJ();

				operacao.consultarProtocolosCNJ();

				AcumuladorExceptions.instance().mostrarExceptionsAcumuladas();

				// Verifica se deve executar novamente em caso de erros
				if (continuarEmCasoDeErro) {
					if (AcumuladorExceptions.instance().isExisteExceptionRegistrada()) {
						progresso.setInformacoes("Aguardando para reiniciar...");
						LOGGER.warn(
								"A operação foi concluída com erros! O envio será reiniciado em 10min... Se desejar, aborte este script.");
						Thread.sleep(10 * 60_000);
						progresso.setInformacoes("");
					} else {
						executar = false;
					}
				} else {
					executar = false;
				}
			} while (executar);
		} finally {
			progresso.setInformacoes("");
			progresso.close();
			progresso = null;
		}

		// Apaga o arquivo com a última página consultada no CNJ. Como a operação foi
		// finalizada com sucesso,
		// não tem sentido mantê-lo, pois ele terá consultado todas as páginas.
		File arquivo = Auxiliar.getArquivoUltimaPaginaConsultaCNJ();

		if (arquivo.exists()) {
			arquivo.delete();
		}

		LOGGER.info("Fim!");
	}

	/**
	 * Consulta os protocolos no CNJ de acordo com o tipo de validação preenchido no arquivo de configuração: TODOS,
	 * TODOS_COM_ERRO (status 6 e 7), APENAS_COM_ERRO_PROCESSADO_COM_ERRO (status 6) e APENAS_COM_ERRO_NO_ARQUIVO
	 * (status 7). Salva a lista de processos em arquivo e também a página atual da consulta para permitir o reinício a
	 * partir dessa página.
	 * 
	 */
	public void consultarProtocolosCNJ() {

		LocalDate dataCorteRemessaAtual = DataJudUtil.getDataCorte();
		TipoRemessaEnum tipoRemessaAtual = DataJudUtil.getTipoRemessa();

		Auxiliar.validarTipoRemessaAtual(tipoRemessaAtual);

		Remessa remessa = remessaDAO.getRemessa(dataCorteRemessaAtual, tipoRemessaAtual, false, true);

		if (remessa == null) {
			throw new RuntimeException("Não foi possível localizar a remessa indicada em config.properties. "
					+ "Certifique-se de que a operação Op_1_BaixaListaDeNumerosDeProcessos foi executada com sucesso.");
		}

		Long totalProtocolosRecebidosDoCNJ = loteProcessoDAO.getQuantidadeProcessosPorRemessaESituacao(remessa,
				getSituacoesProcessosComProtocolo(), true);
		LOGGER.info("Quantidade de protocolos encontrados: " + totalProtocolosRecebidosDoCNJ.intValue());

		if (totalProtocolosRecebidosDoCNJ.intValue() == 0) {
			LOGGER.error("Não foram encontrados na Remessa atual protocolos recebidos do CNJ.");
			return;
		}

		List<LoteProcesso> loteProcessosParaConsultar = loteProcessoDAO.getProcessosPorRemessaESituacao(remessa,
				getSituacoesProcessosComProtocoloPendenteDeConsulta());
		LOGGER.info("Protocolos que ainda precisam ser conferidos no CNJ: " + loteProcessosParaConsultar.size());

		// Atualiza o progresso na interface
		if (progresso != null) {
			progresso.setMax(totalProtocolosRecebidosDoCNJ.intValue());
			progresso.setProgress(totalProtocolosRecebidosDoCNJ.intValue() - loteProcessosParaConsultar.size());
		}

		String parametroProcolo = "";
		String parametroDataInicio = "";
		String parametroDataFim = "";

		if (loteProcessosParaConsultar.size() == 0) {
			LOGGER.info("Nenhum processo possui protocolos pendentes de análise no CNJ!");
			return;
		}

		LOGGER.info("Carregando a data de início e data de fim da remessa.");

		List<String> parametrosData = this.carregarParametrosData(loteProcessosParaConsultar);

		parametroDataInicio = parametrosData.get(0);
		parametroDataFim = parametrosData.get(1);

		LOGGER.info(String.format("Data de início: %s. Data fim: %s.", parametroDataInicio, parametroDataFim));

		LOGGER.info("Carregando os protocolos dos arquivos.");

		Map<String, LoteProcesso> mapProtocolosComLoteProcessos = carregarListaLoteProcessoPorProtocolo(
				loteProcessosParaConsultar);

		URIBuilder builder;

		if (this.tipoValidacaoProtocoloCNJ == null
				|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS.equals(this.tipoValidacaoProtocoloCNJ)) {
			// Constrói a URL com o status "Processado com erro"
			builder = construirBuilderWebServiceCNJ(parametroProcolo, parametroDataInicio, parametroDataFim, "");

			executarConsultasProtocolosCNJ(builder, mapProtocolosComLoteProcessos, TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS);
		} else {
			if (TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS_COM_ERRO.equals(this.tipoValidacaoProtocoloCNJ)
					|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_PROCESSADO_COM_ERRO
							.equals(this.tipoValidacaoProtocoloCNJ)) {
				// Constrói a URL com o status "Processado com erro"
				builder = construirBuilderWebServiceCNJ(parametroProcolo, parametroDataInicio, parametroDataFim,
						SituacaoLoteProcessoEnum.PROCESSADO_COM_ERRO_CNJ.getCodigoCNJ());

				executarConsultasProtocolosCNJ(builder, mapProtocolosComLoteProcessos,
						TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_PROCESSADO_COM_ERRO);
			}

			if (TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS_COM_ERRO.equals(this.tipoValidacaoProtocoloCNJ)
					|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_NO_ARQUIVO.equals(this.tipoValidacaoProtocoloCNJ)) {
				// Constrói a URL com o status "Erro no arquivo"
				builder = construirBuilderWebServiceCNJ(parametroProcolo, parametroDataInicio, parametroDataFim,
						SituacaoLoteProcessoEnum.ERRO_NO_ARQUIVO_CNJ.getCodigoCNJ());

				executarConsultasProtocolosCNJ(builder, mapProtocolosComLoteProcessos,
						TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_NO_ARQUIVO);
			}
			
			if (TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_TODOS_COM_ERRO.equals(this.tipoValidacaoProtocoloCNJ)
					|| TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_GRAVACAO.equals(this.tipoValidacaoProtocoloCNJ)) {
				// Constrói a URL com o status "Erro no arquivo"
				builder = construirBuilderWebServiceCNJ(parametroProcolo, parametroDataInicio, parametroDataFim,
						SituacaoLoteProcessoEnum.ERRO_GRAVACAO_CNJ.getCodigoCNJ());

				executarConsultasProtocolosCNJ(builder, mapProtocolosComLoteProcessos,
						TipoValidacaoProtocoloCNJEnum.VALIDACAO_CNJ_APENAS_COM_ERRO_GRAVACAO);
			}
		}

		if (qtdProtocolosConferidos > 0) {
			LOGGER.warn("Um total de " + qtdProtocolosConferidos + " processos foram ANALISADOS no CNJ.");
		} else {
			LOGGER.warn("Nenhum protocolo foi encontrado para o tipo de validação informada.");
		}

		// Envio finalizado
		Long qtdProcessosPendentes = loteProcessoDAO.getQuantidadeProcessosPorRemessaESituacao(remessa,
				getSituacoesProcessosComProtocoloPendenteDeConsulta(), true);
		LOGGER.info("Protocolos da remessa atual que ainda não foram analisados: " + qtdProcessosPendentes.intValue());

		this.atualizarSituacaoLotesRemessa(remessa);

	}

	private void atualizarSituacaoLotesRemessa(Remessa remessa) {
		try {
			JPAUtil.iniciarTransacao();
			for (Lote lote : remessa.getLotes()) {
				List<SituacaoLoteProcessoEnum> situacoesLoteProcesso = getSituacoesProcessosComConferidos();
				if (!this.considerarXMLsComErro) {
					situacoesLoteProcesso.add(SituacaoLoteProcessoEnum.XML_GERADO_COM_ERRO);
				}

				Long totalDeProcessosNaoConferidos = loteProcessoDAO.getQuantidadeProcessosPorLoteESituacao(lote,
						situacoesLoteProcesso, null, false);
				if (totalDeProcessosNaoConferidos.intValue() == 0) {
					lote.setSituacao(SituacaoLoteEnum.CONFERIDO_CNJ);
					loteDAO.alterar(lote);
				}
			}
			JPAUtil.commit();
			LOGGER.info("Lotes da Remessa Atual atualizado.");
		} catch (Exception e) {
			String origemOperacao = "Erro ao salvar situação do lote durante conferência de protocolos.";
			AcumuladorExceptions.instance().adicionarException(origemOperacao,
					"Erro ao salvar situação do lote durante conferência de protocolos: " + e.getLocalizedMessage(), e,
					true);
			JPAUtil.rollback();
		} finally {
			// JPAUtil.printEstatisticas();
			JPAUtil.close();
		}
	}

	/**
	 * Itera sobre todos os loteProcessos carregados para buscar a menor e maior data do envio dos XMLs.
	 * 
	 * @param loteProcessos Processos com protocolos pendentes
	 * @return Lista com duas String: a primeira com a menor data e a segunda com a maior data
	 */
	private List<String> carregarParametrosData(List<LoteProcesso> loteProcessos) {

		List<String> datas = new ArrayList<>();

		LocalDateTime dataInicio = loteProcessos.get(0).getDataEnvioLocal();
		LocalDateTime dataFim = dataInicio;

		for (LoteProcesso loteProcesso : loteProcessos) {
			LocalDateTime dataModificacaoArquivo = loteProcesso.getDataEnvioLocal();

			if (dataInicio.isAfter(dataModificacaoArquivo)) {
				dataInicio = dataModificacaoArquivo;
			} else if (dataFim.isBefore(dataModificacaoArquivo)) {
				dataFim = dataModificacaoArquivo;
			}
		}

		dataInicio = dataInicio.minusDays(1);
		dataFim = dataFim.plusDays(1);

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

		datas.add(formatter.format(Date.from(dataInicio.atZone(ZoneId.systemDefault()).toInstant())));
		datas.add(formatter.format(Date.from(dataFim.atZone(ZoneId.systemDefault()).toInstant())));

		return datas;
	}

	/**
	 * Realiza a consulta ao webservice do CNJ e gera um arquivo com a lista de processos e suas meta-informações para
	 * cada instância.
	 * 
	 * @param builder                       URIBuilder com a URI a ser consultada
	 * @param mapProtocolosComLoteProcessos Map com a tupla Protocolo/LoteProcesso do Protocolo para conseguir o número
	 *                                      do processo a partir do protocolo retornado pelo serviço do CNJ.
	 * @param setMetaInformacoesProcessosG1 Lista com as meta informações, retornadas pelo CNJ, dos processos analisados
	 *                                      do 1° Grau
	 * @param setMetaInformacoesProcessosG2 Lista com as meta informações, retornadas pelo CNJ, dos processos analisados
	 *                                      do 2° Grau
	 */
	private void executarConsultasProtocolosCNJ(URIBuilder builder,
			Map<String, LoteProcesso> mapProtocolosComLoteProcessos, TipoValidacaoProtocoloCNJEnum tipoValidacaoProtocolo) {

		String agrupadorErrosConsultaProtocolosCNJ = "Consulta de protocolos no CNJ";

		int parametroPaginaAtual = buscarUltimaPaginaConsultadaPorTipoDeValidacao(tipoValidacaoProtocolo) + 1;

		if (parametroPaginaAtual > 0) {
			LOGGER.info("Reiniciando o processo da página " + parametroPaginaAtual + "...");
		}

		// Como o serviço do CNJ não está implementado corretamente, decidiu-se por
		// verificar se a consulta a uma página
		// retornou algum resultado para parar de consultar em vez de pegar o total de
		// registros e dividir pelo tamanho da página.
		// Sempre será efetuada uma consulta a mais, porém esse método fica mais livre
		// de alterações e falhas como a alteração do tamanho
		// da página ou a correção do serviço.
		boolean consultaCNJRetornouResultado = false;
		do {
			URI uri = atualizarParametroURIBuilder(builder, NOME_PARAMETRO_PAGINA,
					Integer.toString(parametroPaginaAtual));

			LOGGER.info(String.format(
					"Consultando a página %s com tipo de validação %s da lista de protocolos no CNJ. URL: %s.",
					parametroPaginaAtual, tipoValidacaoProtocolo, uri.toString()));

			String body = "";
			try {
				body = executarRequisicaoWebServiceCNJ(uri);
			} catch (ParseException | IOException ex) {
				AcumuladorExceptions.instance().adicionarException(agrupadorErrosConsultaProtocolosCNJ,
						ex.getLocalizedMessage(), ex, true);
				return;
			}

			LOGGER.info("* Analisando a resposta JSON...");

			List<MetaInformacaoEnvio> listaMetaInformacoesAnalisadas = carregaMetaInformacoes(body);

			// Verifica se a consulta ao CNJ retornou algum resultado.
			consultaCNJRetornouResultado = !listaMetaInformacoesAnalisadas.isEmpty();

			try {
				JPAUtil.iniciarTransacao();

				this.salvarMetaInformacoes(mapProtocolosComLoteProcessos, listaMetaInformacoesAnalisadas);

				JPAUtil.commit();
			} catch (Exception e) {
				String origemOperacao = "Erro ao salvar meta-informações.";
				AcumuladorExceptions.instance().adicionarException(origemOperacao,
						"Erro ao salvar meta-informações: " + e.getLocalizedMessage(), e, true);
				JPAUtil.rollback();
			} finally {
				// JPAUtil.printEstatisticas();
				JPAUtil.close();
			}

			gravarUltimaPaginaConsultada(tipoValidacaoProtocolo, Integer.toString(parametroPaginaAtual));

			parametroPaginaAtual++;

		} while (consultaCNJRetornouResultado);
	}

	/**
	 * Carrega a lista de meta-informações retornada no objeto JSON.
	 * 
	 * @param body Corpo da resposta da requisição feita ao serviço do CNJ
	 * 
	 * @return Lista de protocolos analisados
	 */
	private List<MetaInformacaoEnvio> carregaMetaInformacoes(String body) {

		List<MetaInformacaoEnvio> resposta = new ArrayList<MetaInformacaoEnvio>();

		try {
			JsonObject rootObject = JsonParser.parseString(body).getAsJsonObject();

			JsonArray processos = rootObject.get("resultado").getAsJsonArray();

			for (JsonElement processoElement : processos) {

				JsonObject processoObject = processoElement.getAsJsonObject();
				String codHash = !processoObject.get("codHash").toString().equals("null")
						? processoObject.get("codHash").getAsString()
						: "";
				String datDataEnvioProtocolo = !processoObject.get("datDataEnvioProtocolo").toString().equals("null")
						? processoObject.get("datDataEnvioProtocolo").getAsString()
						: "";
				String flgExcluido = !processoObject.get("flgExcluido").toString().equals("null")
						? processoObject.get("flgExcluido").getAsString()
						: "";
				String grau = !processoObject.get("grau").toString().equals("null")
						? processoObject.get("grau").getAsString()
						: "";
				String numProtocolo = !processoObject.get("numProtocolo").toString().equals("null")
						? processoObject.get("numProtocolo").getAsString()
						: "";
				String seqProtocolo = !processoObject.get("seqProtocolo").toString().equals("null")
						? processoObject.get("seqProtocolo").getAsString()
						: "";
				String siglaOrgao = !processoObject.get("siglaOrgao").toString().equals("null")
						? processoObject.get("siglaOrgao").getAsString()
						: "";
				String tamanhoArquivo = !processoObject.get("tamanhoArquivo").toString().equals("null")
						? processoObject.get("tamanhoArquivo").getAsString()
						: "";
				String tipStatusProtocolo = !processoObject.get("tipStatusProtocolo").toString().equals("null")
						? processoObject.get("tipStatusProtocolo").getAsString()
						: "";
				String urlArquivo = !processoObject.get("urlArquivo").toString().equals("null")
						? processoObject.get("urlArquivo").getAsString()
						: "";

				if (!numProtocolo.equals("") && !grau.equals("")) {
					resposta.add(new MetaInformacaoEnvio(codHash, datDataEnvioProtocolo, flgExcluido, grau,
							numProtocolo, seqProtocolo, siglaOrgao, tamanhoArquivo, tipStatusProtocolo, urlArquivo));

				} else {
					LOGGER.error(String.format("Erro ao carregar o protocolo %s do Grau %s.", numProtocolo, grau));
				}
			}
		} catch (RuntimeException ex) {
			LOGGER.error("Não foi possível ler o número do protocolo JSON do CNJ: " + body);
		}

		return resposta;
	}

	/**
	 * Carrega a lista de processos a partir dos protocolos.
	 * 
	 * @param mapProtocolosComProcessos        Tupla Protocolo/LoteProcesso
	 * @param listaMetaInformacoesAnalisadasG1 Lista de meta-informações analisadas
	 * 
	 */
	private void salvarMetaInformacoes(Map<String, LoteProcesso> mapProtocolosComProcessos,
			List<MetaInformacaoEnvio> listaMetaInformacoesAnalisadas) {

		for (MetaInformacaoEnvio metaInformacao : listaMetaInformacoesAnalisadas) {
			// Pesquisa o loteProcesso a partir dos protocolos
			LoteProcesso loteProcesso = mapProtocolosComProcessos.get(metaInformacao.getNumProtocolo());

			if (loteProcesso != null) {
				loteProcesso.setDataRecebimentoCNJ(metaInformacao.getLocalDateTimeEnvioProtocolo());
				loteProcesso.setHashCNJ(metaInformacao.getCodHash());

				SituacaoLoteProcessoEnum situacaoCNJ = SituacaoLoteProcessoEnum
						.criarApartirCodigoCNJ(metaInformacao.getTipStatusProtocolo());

				if (situacaoCNJ == null) {
					LOGGER.error(String.format("Não foi possível converter a situação do protocolo: %s.",
							metaInformacao.getTipStatusProtocolo()));
				} else {
					loteProcesso.setSituacao(situacaoCNJ);

					if (situacaoCNJ.equals(SituacaoLoteProcessoEnum.PROCESSADO_COM_SUCESSO_CNJ)) {
						LOGGER.debug("Protocolo baixado com SUCESSO: " + loteProcesso.getProtocoloCNJ() + ", processo '"
								+ loteProcesso.getChaveProcessoCNJ().getNumeroProcesso() + "'");
					} else if (situacaoCNJ.isSituacaoErroCNJ()) {
						LOGGER.warn("Protocolo baixado com ERRO: " + loteProcesso.getProtocoloCNJ() + ", processo '"
								+ loteProcesso.getChaveProcessoCNJ().getNumeroProcesso() + "', situação '"
								+ loteProcesso.getSituacao().getLabel() + "'");
					} else {
						LOGGER.info("Protocolo NÃO baixado ainda: " + loteProcesso.getProtocoloCNJ() + ", processo '"
								+ loteProcesso.getChaveProcessoCNJ().getNumeroProcesso() + "', situação '"
								+ loteProcesso.getSituacao().getLabel() + "'");
					}
				}
				loteProcessoDAO.incluirOuAlterar(loteProcesso);

				qtdProtocolosConferidos++;

				if (progresso != null) {
					progresso.incrementProgress();
				}
			} else {
				LOGGER.warn("Não foi encontrado para a presente Remessa um processo com o seguinte protocolo de envio: "
						+ metaInformacao.getNumProtocolo());
			}
		}
	}

	/**
	 * Atualiza o valor de um parâmetro do URIBuilder retornando a URI alterada
	 * 
	 * @param builder        URIBuilder
	 * @param nomeParametro  Nome do parâmetro contido no URIBuilder
	 * @param valorParametro Valor do parâmetro a ser atualizado.
	 * 
	 * @return Nova URI com o parâmetro atualizado
	 */
	private URI atualizarParametroURIBuilder(URIBuilder builder, String nomeParametro, String valorParametro) {

		builder.setParameter(nomeParametro, valorParametro);

		try {
			return builder.build();
		} catch (URISyntaxException e) {
			throw new RuntimeException("Erro na criação da URL para consultar protocolos.", e);
		}
	}

	/**
	 * Constrói o URIBuilder a partir do parâmetro Parametro.url_webservice_cnj concatenada com a string "/protocolos" e
	 * os parâmetros da URL passados para esse método.
	 * 
	 * @param parametroProcolo    Número do protocolo
	 * @param parametroDataInicio Data de início do envio
	 * @param parametroDataFim    Data de Fim do Envio
	 * @param parametroStatus     Status do protocolo: 1 (Aguardando processamento), 3 (Processado com sucesso), 4
	 *                            (Enviado), 5 (Duplicado), 6 (Processado com Erro), 7 (Erro no Arquivo)
	 * 
	 * @return URIBuilder composto por Parametro.url_webservice_cnj mais "/protocolos" e os parâmetros passados para o
	 *         método.
	 */
	private URIBuilder construirBuilderWebServiceCNJ(String parametroProcolo, String parametroDataInicio,
			String parametroDataFim, String parametroStatus) {

		// Campo "status" da URL de consulta de protocolos:
		// 1 = Aguardando processamento
		// 3 = Processado com sucesso
		// 4 = Enviado
		// 5 = Duplicado
		// 6 = Processado com Erro
		// 7 = Erro no Arquivo

		// Monta a URL para consultar protocolos no CNJ.
		// Exemplo de URL:
		// https://www.cnj.jus.br/modelo-de-transferencia-de-dados/v1/processos/protocolos?protocolo=&dataInicio=2020-07-21&dataFim=2020-07-22&status=6&page=0
		final String url = Auxiliar.getParametroConfiguracao(Parametro.url_webservice_cnj, true) + "/protocolos";

		URIBuilder builder = null;

		try {
			builder = new URIBuilder(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Erro na criação da URL para consultar protocolos.", e);
		}

		builder.setParameter(NOME_PARAMETRO_PROTOCOLO, parametroProcolo)
				.setParameter(NOME_PARAMETRO_DATA_INICIO, parametroDataInicio)
				.setParameter(NOME_PARAMETRO_DATA_FIM, parametroDataFim)
				.setParameter(NOME_PARAMETRO_STATUS, parametroStatus);

		return builder;
	}

	/**
	 * Realiza a requisição HTTP ao serviço do CNJ. Mesmo quando o serviço está ativo ele retorna o erro 504 algumas
	 * vezes, dessa forma são feitas 10 tentativas com 5 minutos entre cada uma antes de lançar a exceção IOException.
	 * 
	 * @param uri URI do serviço
	 * 
	 * @return O corpo (body) da resposta do serviço
	 * 
	 * @throws IOException
	 */
	private String executarRequisicaoWebServiceCNJ(URI uri) throws IOException {

		HttpGet get = new HttpGet(uri);
		HttpUtil.adicionarCabecalhoAutenticacao(get);

		// TODO: Validar esse timeout considerando os muitos dados de produção
		// // Timeout
		// int CONNECTION_TIMEOUT_MS = 300_000; // Timeout in millis (5min)
		// RequestConfig requestConfig = RequestConfig.custom()
		// .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
		// .setConnectTimeout(CONNECTION_TIMEOUT_MS)
		// .setSocketTimeout(CONNECTION_TIMEOUT_MS)
		// .build();
		// post.setConfig(requestConfig);

		long tempo;
		HttpResponse response = null;
		int statusCode = 0;

		// Como o erro 504 acontece com alguma frequência mesmo quando o
		// serviço está funcionando, faz a tentativa até 10 vezes
		int tentativasTimeOut = 0;

		String body = "";

		try {
			do {
				// Executa o GET
				tempo = System.currentTimeMillis();
				response = httpClient.execute(get);
				HttpEntity result = response.getEntity();
				body = EntityUtils.toString(result, Charset.forName("UTF-8"));
				statusCode = response.getStatusLine().getStatusCode();
				LOGGER.info("* Resposta em " + tempo + "ms (" + statusCode + ")");
				if (statusCode == 504 && tentativasTimeOut < 10) {
					LOGGER.info("* Aguardando 5 minutos...");
					try {
						Thread.sleep(5 * 60_000);
					} catch (InterruptedException e) {
						LOGGER.warn("Erro ao tentar pausar por 5 minutos...");
					}
					LOGGER.info("* Tentando novamente...");
				}
			} while (statusCode == 504 && tentativasTimeOut++ < 10); // Timeout
		} catch (ClientProtocolException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		} finally {
			if (response != null) {
				EntityUtils.consumeQuietly(response.getEntity());
			}
		}

		if (statusCode != 200 && statusCode != 201) {
			throw new IOException(
					"Falha ao conectar no Webservice do CNJ (codigo " + statusCode + ", esperado 200 ou 201)");
		}

		return body;
	}

	/**
	 * Retorna a última página consultada de acordo com o status do processamento do protocolo. A página inicial é a 0.
	 * 
	 * @param tipoValidacaoProtocolo Tipo de validação de protocolos utilizada no processamento de protocolos no CNJ
	 * @return -1 ou a última página consultada
	 */
	private int buscarUltimaPaginaConsultadaPorTipoDeValidacao(TipoValidacaoProtocoloCNJEnum tipoValidacaoProtocolo) {

		File arquivoPaginaAtual = Auxiliar.getArquivoUltimaPaginaConsultaCNJ();

		int numUltimaPaginaConsultada = -1;

		if (arquivoPaginaAtual.exists() && arquivoPaginaAtual.length() != 0) {
			try {
				Pattern padraoER = Pattern
						.compile(String.format(PADRAO_ER_ARQUIVO_ULTIMA_PAGINA_CONSULTADA, tipoValidacaoProtocolo.getCodigo()));

				List<String> conteudoArquivo = new ArrayList<>(
						Files.readAllLines(arquivoPaginaAtual.toPath(), StandardCharsets.UTF_8));

				for (int i = 0; i < conteudoArquivo.size(); i++) {
					Matcher matcher = padraoER.matcher(conteudoArquivo.get(i));

					if (matcher.find()) {
						return Integer.valueOf(matcher.group(2));
					}
				}
			} catch (IOException e) {
				LOGGER.warn("* Não foi possível ler o arquivo " + arquivoPaginaAtual.toString());
			} catch (NumberFormatException e) {
				LOGGER.warn("* Não foi possível converter para inteiro o conteúdo do arquivo "
						+ arquivoPaginaAtual.toString());
			}
		}

		return numUltimaPaginaConsultada;
	}

	private void gravarUltimaPaginaConsultada(TipoValidacaoProtocoloCNJEnum tipoValidacaoProtocolo, String pagina) {

		File arquivoPaginaAtual = Auxiliar.getArquivoUltimaPaginaConsultaCNJ();

		if (arquivoPaginaAtual.exists() && arquivoPaginaAtual.length() != 0) {
			try {
				Pattern padraoER = Pattern
						.compile(String.format(PADRAO_ER_ARQUIVO_ULTIMA_PAGINA_CONSULTADA, tipoValidacaoProtocolo.getCodigo()));

				List<String> conteudoArquivo = new ArrayList<>(
						Files.readAllLines(arquivoPaginaAtual.toPath(), StandardCharsets.UTF_8));

				for (int i = 0; i < conteudoArquivo.size(); i++) {
					Matcher matcher = padraoER.matcher(conteudoArquivo.get(i));

					if (matcher.find()) {
						// Encontrou uma linha com o formato, então apenas atualiza.
						conteudoArquivo.set(i, String.format("%s %s", tipoValidacaoProtocolo.getCodigo(), pagina));
						Files.write(arquivoPaginaAtual.toPath(), conteudoArquivo, StandardCharsets.UTF_8);
						return;
					}
				}
				// Não encontrou uma linha com o formato
				FileUtils.write(arquivoPaginaAtual, String.format("%s %s", tipoValidacaoProtocolo, pagina),
						StandardCharsets.UTF_8, true);

			} catch (IOException e) {
				LOGGER.warn("* Não foi possível ler o arquivo " + arquivoPaginaAtual.toString());
			} catch (NumberFormatException e) {
				LOGGER.warn("* Não foi possível converter para inteiro o conteúdo do arquivo "
						+ arquivoPaginaAtual.toString());
			}
		} else {
			// Arquivo não existente
			try {
				FileUtils.write(arquivoPaginaAtual, String.format("%s %s", tipoValidacaoProtocolo, pagina),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				LOGGER.warn("Não foi possível escrever o número da página atual consultada no arquivo "
						+ arquivoPaginaAtual.getPath());
			}
		}
	}

	/**
	 * Itera sobre todos os loteProcessos com o número do protocolo para associá-los ao respectivo loteProcesso.
	 * 
	 * @param loteProcessos
	 * @return HashMap com a dupla Protocolo/LoteProcesso de todos os processos com protocolo a serem pesquisados.
	 */
	private Map<String, LoteProcesso> carregarListaLoteProcessoPorProtocolo(List<LoteProcesso> loteProcessos) {
		Map<String, LoteProcesso> protocolosComProcessos = new HashMap<String, LoteProcesso>();

		for (LoteProcesso loteProcesso : loteProcessos) {
			if (loteProcesso.getProtocoloCNJ() != null) {
				protocolosComProcessos.put(loteProcesso.getProtocoloCNJ(), loteProcesso);
			} else {
				LOGGER.warn("Não foi possível carregar o protocolo do processo: "
						+ loteProcesso.getChaveProcessoCNJ().getNumeroProcesso());
			}
		}
		return protocolosComProcessos;
	}

	private static List<SituacaoLoteProcessoEnum> getSituacoesProcessosComProtocolo() {
		List<SituacaoLoteProcessoEnum> situacoes = new ArrayList<SituacaoLoteProcessoEnum>();
		situacoes.addAll(getSituacoesProcessosComProtocoloPendenteDeConsulta());
		situacoes.addAll(getSituacoesProcessosComConferidos());
		return situacoes;
	}

	private static List<SituacaoLoteProcessoEnum> getSituacoesProcessosComProtocoloPendenteDeConsulta() {
		List<SituacaoLoteProcessoEnum> situacoes = new ArrayList<SituacaoLoteProcessoEnum>();
		situacoes.add(SituacaoLoteProcessoEnum.ENVIADO);
		situacoes.add(SituacaoLoteProcessoEnum.RECEBIDO_CNJ);
		situacoes.add(SituacaoLoteProcessoEnum.AGUARDANDO_PROCESSAMENTO_CNJ);
		return situacoes;
	}

	private static List<SituacaoLoteProcessoEnum> getSituacoesProcessosComConferidos() {
		List<SituacaoLoteProcessoEnum> situacoes = new ArrayList<SituacaoLoteProcessoEnum>();
		situacoes.add(SituacaoLoteProcessoEnum.PROCESSADO_COM_SUCESSO_CNJ);
		situacoes.add(SituacaoLoteProcessoEnum.DUPLICADO_CNJ);
		situacoes.add(SituacaoLoteProcessoEnum.PROCESSADO_COM_ERRO_CNJ);
		situacoes.add(SituacaoLoteProcessoEnum.ERRO_NO_ARQUIVO_CNJ);
		situacoes.add(SituacaoLoteProcessoEnum.ERRO_GRAVACAO_CNJ);
		return situacoes;
	}

}
