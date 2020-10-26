package br.jus.trt4.justica_em_numeros_2016.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import br.jus.trt4.justica_em_numeros_2016.auxiliar.AcumuladorExceptions;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.Auxiliar;
import br.jus.trt4.justica_em_numeros_2016.auxiliar.ProgressoInterfaceGrafica;
import br.jus.trt4.justica_em_numeros_2016.enums.BaseEmAnaliseEnum;
import br.jus.trt4.justica_em_numeros_2016.enums.Parametro;

/**
 * Monta uma lista de processos, conforme o parâmetro "tipo_carga_xml" do arquivo "config.properties",
 * e grava na pasta "output/.../Xg" (onde 'X' representa o número da instância - '1' ou '2'), 
 * em arquivos com nome "lista_processos.txt".
 * 
 * @author felipe.giotto@trt4.jus.br
 */
public class Op_1_BaixaListaDeNumerosDeProcessos implements AutoCloseable {

	private static final Logger LOGGER = LogManager.getLogger(Op_1_BaixaListaDeNumerosDeProcessos.class);
	private int grau;
	private boolean deveProcessarProcessosPje;
	private boolean deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje;
	private boolean deveProcessarProcessosSistemaLegadoMigradosParaOPJe;
	
	private Connection conexaoBasePrincipal;
	private Connection conexaoBasePrincipalLegado;
	private Connection conexaoBaseStagingEGestao;
	private static final Pattern pCargaProcesso = Pattern.compile("^PROCESSO (\\d{7}\\-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4})$");
	private static final Pattern pMesAnoCorte = Pattern.compile("^(\\d+)-(\\d+)$");
	
	
	public static void main(String[] args) throws SQLException, IOException {
		ProgressoInterfaceGrafica progresso = new ProgressoInterfaceGrafica("(1/6) Baixa lista de números de processos");
		try {
			progresso.setMax(3);
			
			Auxiliar.prepararPastaDeSaida();
			progresso.incrementProgress();
			
			// Lista de processos do primeiro grau
			if (Auxiliar.deveProcessarPrimeiroGrau()) {
				gerarListaProcessos(1);
			}
			progresso.incrementProgress();
			
			// Lista de processos do segundo grau
			if (Auxiliar.deveProcessarSegundoGrau()) {
				gerarListaProcessos(2);
			}
			progresso.incrementProgress();
			
			AcumuladorExceptions.instance().mostrarExceptionsAcumuladas();
			LOGGER.info("Fim!");
		} finally {
			progresso.close();
		}
	}

	
	private static void gerarListaProcessos(int grau) throws SQLException, IOException {
		
		// Executa consultas e grava arquivo XML
		try (Op_1_BaixaListaDeNumerosDeProcessos baixaDados = new Op_1_BaixaListaDeNumerosDeProcessos(grau)) {
			baixaDados.baixarListaProcessos();
		}
	}
	
	
	public Op_1_BaixaListaDeNumerosDeProcessos(int grau) {
		this.grau = grau;
		this.deveProcessarProcessosPje = Auxiliar.deveProcessarProcessosPje();
		this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje = Auxiliar.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje();
		this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe = Auxiliar.deveProcessarProcessosSistemaLegadoMigradosParaOPJe();
	}

	
	public void baixarListaProcessos() throws IOException, SQLException {
		
		// Verifica quais os critérios selecionados pelo usuário, no arquivo "config.properties",
		// pra escolher os processos que serão analisados.
		String tipoCarga = Auxiliar.getParametroConfiguracao(Parametro.tipo_carga_xml, true);
		String mesAnoCorte = Auxiliar.getParametroConfiguracao(Parametro.mes_ano_corte, true);
		LOGGER.info("Executando consulta " + tipoCarga + "...");
		ResultSet rsConsultaProcessosPje = null;
		ResultSet rsConsultaProcessosMigradosLegado = null;
		ResultSet rsConsultaProcessosNaoMigradosLegado = null;
		String pastaIntermediariaPje = Auxiliar.getPastaResources(BaseEmAnaliseEnum.PJE, this.grau);
		String pastaIntermediariaLegado = Auxiliar.getPastaResources(BaseEmAnaliseEnum.LEGADO, this.grau);

		if ("TESTES".equals(tipoCarga)) {

			// Se usuário selecionou carga "TESTES" no parâmetro "tipo_carga_xml", pega um lote qualquer de processos
			if (this.deveProcessarProcessosPje) {
				String sql = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaPje + "/carga_testes.sql");
				rsConsultaProcessosPje = getConexaoBasePrincipalPJe().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD).executeQuery(sql);
			}
			if (this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe) {
				String sqlMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_testes_migrados.sql");
				rsConsultaProcessosMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD).executeQuery(sqlMigradosLegado);
			}
			if (this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje) {
				String sqlNaoMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_testes_nao_migrados.sql");
				rsConsultaProcessosNaoMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD).executeQuery(sqlNaoMigradosLegado);
			}
			
			LOGGER.warn(">>>>>>>>>> CUIDADO! Somente uma fração dos dados está sendo carregada, para testes! Atente ao parâmetro 'tipo_carga_xml', nas configurações!! <<<<<<<<<<");
			
		} else if (tipoCarga.startsWith("PROCESSO ")) {

			// Se usuário preencheu um número de processo no parâmetro "tipo_carga_xml", carrega
			// somente os dados dele
			Matcher m = pCargaProcesso.matcher(tipoCarga);
			if (!m.find()) {
				throw new RuntimeException("Parâmetro 'tipo_carga_xml' não especifica corretamente o processo que precisa ser baixado! Verifique o arquivo 'config.properties'");
			}
			String numeroProcesso = m.group(1);
			
			// Carrega o SQL do arquivo
			if (this.deveProcessarProcessosPje) {
				String sql = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaPje + "/carga_um_processo.sql");
				PreparedStatement ps = getConexaoBasePrincipalPJe().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				ps.setString(1, numeroProcesso);
				rsConsultaProcessosPje = ps.executeQuery();
			}
			if (this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe) {
				String sqlMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_um_processo_migrado.sql");
				PreparedStatement psMigradosLegado = getConexaoBasePrincipalLegado().prepareStatement(sqlMigradosLegado, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				psMigradosLegado.setString(1, numeroProcesso);
				rsConsultaProcessosMigradosLegado = psMigradosLegado.executeQuery();
			}
			if (this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje) {
				String sqlNaoMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_um_processo_nao_migrado.sql");
				PreparedStatement psNaoMigradosLegado = getConexaoBasePrincipalLegado().prepareStatement(sqlNaoMigradosLegado, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				psNaoMigradosLegado.setString(1, numeroProcesso);
				rsConsultaProcessosNaoMigradosLegado = psNaoMigradosLegado.executeQuery();
			}
			
			LOGGER.warn(">>>>>>>>>> CUIDADO! Somente estão sendo carregados os dados do processo " + numeroProcesso + "! Atente ao parâmetro 'tipo_carga_xml', nas configurações!! <<<<<<<<<<");
			
		} else if ("COMPLETA".equals(tipoCarga)) {
			// Se usuário selecionou carga "COMPLETA" no parâmetro "tipo_carga_xml", 
			// gera os XMLs de todos os processos que obedecerem às regras descritas no site do CNJ
			if (this.deveProcessarProcessosPje) {
				Matcher m = pMesAnoCorte.matcher(mesAnoCorte);
				if (!m.find()) {
					throw new RuntimeException("Parâmetro 'mes_ano_corte' não especifica corretamente o ano e o mês que precisam ser baixados! Verifique o arquivo 'config.properties'");
				}
				String dataFinalComHoras = this.getDataCorte(m, false, true);
				String dataFinalSemHoras = this.getDataCorte(m, false, false);
				LOGGER.info("* Considerando movimentações entre '01-01-2015 00:00:00.000' e '" + dataFinalComHoras + "'");
				
				String sql = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaPje + "/carga_completa_egestao_" + this.grau + "g.sql");
				getConexaoBaseStagingEGestao().createStatement().execute("SET search_path TO pje_eg");
				PreparedStatement statement = getConexaoBaseStagingEGestao().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statement.setDate(1, java.sql.Date.valueOf(dataFinalSemHoras));
				statement.setDate(2, java.sql.Date.valueOf(dataFinalSemHoras));
				statement.setString(3, dataFinalComHoras);
				statement.setFetchSize(100);
				rsConsultaProcessosPje = statement.executeQuery();
			}
			if (this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe) {
				String sqlMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_completa_migrados.sql");
				Statement statementMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementMigradosLegado.setFetchSize(100);
				rsConsultaProcessosMigradosLegado = statementMigradosLegado.executeQuery(sqlMigradosLegado);
			}
			if (this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje) {
				String sqlNaoMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_completa_nao_migrados.sql");
				Statement statementNaoMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementNaoMigradosLegado.setFetchSize(100);
				rsConsultaProcessosNaoMigradosLegado = statementNaoMigradosLegado.executeQuery(sqlNaoMigradosLegado);
			}
			
		} else if ("TODOS_COM_MOVIMENTACOES".equals(tipoCarga)) {

			// Se usuário selecionou carga "TODOS_COM_MOVIMENTACOES" no parâmetro "tipo_carga_xml", 
			// gera os XMLs de todos os processos que tiveram qualquer movimentação processual na 
			// tabela tb_processo_evento. 
			if (this.deveProcessarProcessosPje) {
				String sql = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaPje + "/carga_todos_com_movimentacoes.sql");
				Statement statement = getConexaoBasePrincipalPJe().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statement.setFetchSize(100);
				rsConsultaProcessosPje = statement.executeQuery(sql);
			}
			if (this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe) {
				String sqlMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_todos_com_movimentacoes_migrados.sql");
				Statement statementMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementMigradosLegado.setFetchSize(100);
				rsConsultaProcessosMigradosLegado = statementMigradosLegado.executeQuery(sqlMigradosLegado);
			}
			if (this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje) {
				String sqlNaoMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_todos_com_movimentacoes_nao_migrados.sql");
				Statement statementNaoMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementNaoMigradosLegado.setFetchSize(100);
				rsConsultaProcessosNaoMigradosLegado = statementNaoMigradosLegado.executeQuery(sqlNaoMigradosLegado);
			}
			
		} else if (tipoCarga.equals("MENSAL")) {
			
			if (this.deveProcessarProcessosPje) {
				// Se usuário selecionou carga "MENSAL" no parâmetro "tipo_carga_xml", utiliza as
				// regras definidas pelo CNJ
				Matcher m = pMesAnoCorte.matcher(mesAnoCorte);
				if (!m.find()) {
					throw new RuntimeException("Parâmetro 'mes_ano_corte' não especifica corretamente o ano e o mês que precisam ser baixados! Verifique o arquivo 'config.properties'");
				}
				// Identifica o início e o término do mês selecionado
				String dataInicial = this.getDataCorte(m, true, true);
				String dataFinal = this.getDataCorte(m, false, true);
				LOGGER.info("* Considerando movimentações entre '" + dataInicial + "' e '" + dataFinal + "'");
				
				// Carrega o SQL do arquivo
				String sql = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaPje + "/carga_mensal.sql");
				PreparedStatement statement = getConexaoBasePrincipalPJe().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statement.setString(1, dataInicial);
				statement.setString(2, dataFinal);
				statement.setFetchSize(100);
				rsConsultaProcessosPje = statement.executeQuery();
			}
			if (this.deveProcessarProcessosSistemaLegadoMigradosParaOPJe) {
				String sqlMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_mensal_migrados.sql");
				Statement statementMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementMigradosLegado.setFetchSize(100);
				rsConsultaProcessosMigradosLegado = statementMigradosLegado.executeQuery(sqlMigradosLegado);
			}
			if (this.deveProcessarProcessosSistemaLegadoNaoMigradosParaOPje) {
				String sqlNaoMigradosLegado = Auxiliar.lerConteudoDeArquivo("src/main/resources/sql/op_1_baixa_lista_processos/" + pastaIntermediariaLegado + "/carga_mensal_nao_migrados.sql");
				Statement statementNaoMigradosLegado = getConexaoBasePrincipalLegado().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.FETCH_FORWARD);
				statementNaoMigradosLegado.setFetchSize(100);
				rsConsultaProcessosNaoMigradosLegado = statementNaoMigradosLegado.executeQuery(sqlNaoMigradosLegado);
			}
			
		} else {
			throw new RuntimeException("Valor desconhecido para o parâmetro 'tipo_carga_xml': " + tipoCarga);
		}
		
		if (rsConsultaProcessosPje != null) {		
			// Itera sobre os processos encontrados no PJE
			gravarListaProcessos(rsConsultaProcessosPje, Auxiliar.getArquivoListaProcessosPje(this.grau));
		}
		if (rsConsultaProcessosMigradosLegado != null) {
			// Itera sobre os processos encontrados no Sistema Judicial Legado e que já foram migrados
			gravarListaProcessos(rsConsultaProcessosMigradosLegado, Auxiliar.getArquivoListaProcessosSistemaLegadoMigradosParaOPJe(this.grau));
		}
		if (rsConsultaProcessosNaoMigradosLegado != null) {
			// Itera sobre os processos encontrados no Sistema Judicial Legado e que NÃO foram migrados
			gravarListaProcessos(rsConsultaProcessosNaoMigradosLegado, Auxiliar.getArquivoListaProcessosSistemaLegadoNaoMigradosParaOPje(this.grau));
		}
	}
	
	private String getDataCorte(Matcher matcher, boolean returnarDataInicio, boolean informarHoras) {
		String data  = null;

		int ano = Integer.parseInt(matcher.group(1));
		int mes = Integer.parseInt(matcher.group(2));
		if (returnarDataInicio) {
			data = ano + "-" + mes + (informarHoras ? "-1 00:00:00.000" : "");
		} else {
			int maiorDiaNoMes = new GregorianCalendar(ano, (mes - 1), 1).getActualMaximum(Calendar.DAY_OF_MONTH);
			data = ano + "-" + mes + "-" + maiorDiaNoMes + (informarHoras ?" 23:59:59.999" : "");
		}
		return data;
	}
	
	public static void gravarListaProcessos(ResultSet rsConsultaProcessos, File arquivoSaida) throws IOException, SQLException {
		// Itera sobre os processos encontrados
		Set<String> listaProcessos = new TreeSet<>();
		try {
			int qtdProcessos = 0;
			long tempo = System.currentTimeMillis();
			LOGGER.info("Iterando sobre a lista de processos...");
			while (rsConsultaProcessos.next()) {
				qtdProcessos++;
				String nrProcesso = rsConsultaProcessos.getString("nr_processo");
				listaProcessos.add(nrProcesso);
				
				// Mostra a quantidade de processos analisados a cada 15 segundos
				if (System.currentTimeMillis() - tempo > 15_000) {
					tempo = System.currentTimeMillis();
					LOGGER.info("Processos até agora: " + qtdProcessos);
				}
			}
		} finally {
			rsConsultaProcessos.close();
		}
		
		// Salva a lista de processos em um arquivo "properties"
		gravarListaProcessosEmArquivo(listaProcessos, arquivoSaida);
	}
	
	
	public Connection getConexaoBasePrincipalPJe() throws SQLException {
		if (conexaoBasePrincipal == null) {
			conexaoBasePrincipal = Auxiliar.getConexao(this.grau, BaseEmAnaliseEnum.PJE);
			conexaoBasePrincipal.setAutoCommit(false);
		}
		return conexaoBasePrincipal;
	}
	
	public Connection getConexaoBasePrincipalLegado() throws SQLException {
		if (conexaoBasePrincipalLegado == null) {
			conexaoBasePrincipalLegado = Auxiliar.getConexao(this.grau, BaseEmAnaliseEnum.LEGADO);
			conexaoBasePrincipalLegado.setAutoCommit(false);
		}
		return conexaoBasePrincipalLegado;
	}
	
	public Connection getConexaoBaseStagingEGestao() throws SQLException {
		if (conexaoBaseStagingEGestao == null) {
			conexaoBaseStagingEGestao = Auxiliar.getConexaoStagingEGestao(grau);
			conexaoBaseStagingEGestao.setAutoCommit(false);
		}
		return conexaoBaseStagingEGestao;
	}
	
	public static void gravarListaProcessosEmArquivo(Set<String> listaProcessos, File arquivoSaida) throws IOException {
		arquivoSaida.getParentFile().mkdirs();
		FileWriter fw = new FileWriter(arquivoSaida);
		try {
			for (String processo: listaProcessos) {
				fw.append(processo);
				fw.append("\r\n");
			}
		} finally {
			fw.close();
		}
		LOGGER.info("Arquivo gerado com lista de " + listaProcessos.size() + " processo(s): " + arquivoSaida);
	}

	
	/**
	 * Fecha conexão com o PJe  e com o sistema judicial legado
	 */
	@Override
	public void close() {

		Auxiliar.fechar(conexaoBaseStagingEGestao);
		conexaoBaseStagingEGestao = null;
		
		Auxiliar.fechar(conexaoBasePrincipal);
		conexaoBasePrincipal = null;
		
		Auxiliar.fechar(conexaoBasePrincipalLegado);
		conexaoBasePrincipalLegado = null;
	}
}
