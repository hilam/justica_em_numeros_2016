--OBSERVAÇÃO: Se o schema criado no Regional for diferente de 'datajud', ajustar o presente arquivo e o persistence.

--------------------------------------------------------------
--------------------------------------------------------------
-----------------------------DATAJUD--------------------------
--------------------------------------------------------------
----------------------------REMESSA---------------------------
--------------------------------------------------------------
-- SEQUENCE: datajud.sq_tb_remessa


CREATE SEQUENCE datajud.sq_tb_remessa
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;
	
-- Table: datajud.tb_remessa

CREATE TABLE datajud.tb_remessa
(
	id_remessa bigint NOT NULL DEFAULT nextval('datajud.sq_tb_remessa'::regclass),
    dt_corte DATE NOT NULL,
    en_tipo varchar(1) NOT NULL,
    constraint tb_remessa_ck01 check (en_tipo in ('C','M')),
    constraint tb_remessa_uk01 UNIQUE(dt_corte, en_tipo),
    constraint tb_remessa_pk primary key (id_remessa)
);

COMMENT ON COLUMN datajud.tb_remessa.dt_corte
    IS 'Data de corte da Remessa.';

COMMENT ON COLUMN datajud.tb_remessa.en_tipo
    IS 'Indica se a Remessa é Completa (C) ou Mensal (M).';

    
--------------------------------------------------------------
-----------------------------Lote-----------------------------
--------------------------------------------------------------
-- SEQUENCE: datajud.sq_tb_lote


CREATE SEQUENCE datajud.sq_tb_lote
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;
	
-- Table: datajud.tb_lote

CREATE TABLE datajud.tb_lote
(
	id_lote bigint NOT NULL DEFAULT nextval('datajud.sq_tb_lote'::regclass),
    nm_numero varchar(10) NOT NULL,
    en_situacao varchar(1),
    id_remessa bigint NOT NULL,
    constraint tb_lote_pk primary key (id_lote),
    constraint tb_lote_ck01 check (en_situacao in ('1','2','3','4','5'))
);

COMMENT ON COLUMN datajud.tb_lote.nm_numero
    IS 'Número do Lote.';
    
COMMENT ON COLUMN datajud.tb_lote.en_situacao
 	IS 'Situação Lote que será enviado ao CNJ. 1:Criado Parcialmente; 2:Criado Com Erros; 3: Criado sem Erros; 4:Enviado; 5:Conferido no CNJ';
    
alter table datajud.tb_lote
add constraint tb_lote_fk01 foreign key (id_remessa)
references datajud.tb_remessa (id_remessa);

--------------------------------------------------------------
-----------------------ChaveProcessoCNJ-----------------------
--------------------------------------------------------------
-- SEQUENCE: datajud.sq_tb_chave_processo_cnj


CREATE SEQUENCE datajud.sq_tb_chave_processo_cnj
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;
	
-- Table: datajud.tb_chave_processo_cnj

CREATE TABLE datajud.tb_chave_processo_cnj
(
	id_chave_processo_cnj bigint NOT NULL DEFAULT nextval('datajud.sq_tb_chave_processo_cnj'::regclass),
    cd_classe_judicial varchar(15) NOT NULL,
    nr_processo varchar(30) NOT NULL,
    nm_grau varchar(1) NOT NULL,
    cd_orgao_julgador bigint NOT NULL,
    constraint tb_chave_processo_cnj_ck01 check (nm_grau in ('1','2')),
    constraint tb_chave_processo_cnj_uk01 UNIQUE(cd_classe_judicial, nr_processo, nm_grau, cd_orgao_julgador),
    constraint tb_chave_processo_cnj_pk primary key (id_chave_processo_cnj)
);

COMMENT ON COLUMN datajud.tb_chave_processo_cnj.cd_classe_judicial
    IS 'Código da Classe Processual.';
    
COMMENT ON COLUMN datajud.tb_chave_processo_cnj.nr_processo
    IS 'Número do Processo.';

COMMENT ON COLUMN datajud.tb_chave_processo_cnj.nm_grau
    IS 'Instância do processo.';
    
COMMENT ON COLUMN datajud.tb_chave_processo_cnj.cd_orgao_julgador
    IS 'Código do Órgão Julgador do processo.';
    


--------------------------------------------------------------
-----------------------------LoteProcesso---------------------
--------------------------------------------------------------
-- SEQUENCE: datajud.sq_tb_lote_processo


CREATE SEQUENCE datajud.sq_tb_lote_processo
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;
	
-- Table: datajud.tb_lote_processo

CREATE TABLE datajud.tb_lote_processo
(
	id_lote_processo bigint NOT NULL DEFAULT nextval('datajud.sq_tb_lote_processo'::regclass),
    dh_envio_local timestamp,
    nm_protocolo_cnj varchar(60),
    nm_hash_cnj varchar(64),
    dh_recebimento_cnj timestamp,
    en_situacao varchar(2),
    en_origem_processo varchar(1),
    conteudo_xml bytea,
    id_lote bigint NOT NULL,
    id_chave_processo_cnj bigint NOT NULL,
    constraint tb_lote_processo_ck01 check (en_situacao in ('1','2','3','4','5','6','7','8','9')),
    constraint tb_lote_processo_ck02 check (en_origem_processo in ('H','L','P')),
    constraint tb_lote_processo_pk primary key (id_lote_processo)
);

COMMENT ON COLUMN datajud.tb_lote_processo.dh_envio_local
    IS 'Momento em que o XML do processo é enviado.';
    
COMMENT ON COLUMN datajud.tb_lote_processo.nm_protocolo_cnj
    IS 'Número do Protocolo gerado pelo CNJ após o envio do XML.';

COMMENT ON COLUMN datajud.tb_lote_processo.nm_hash_cnj
    IS 'Hash Code gerado pelo CNJ após o envio do XML.';

COMMENT ON COLUMN datajud.tb_lote_processo.dh_recebimento_cnj
    IS 'Momento em que o XML é recebido pelo CNJ.';
    
COMMENT ON COLUMN datajud.tb_lote_processo.en_situacao
 	IS 'Situação do XML que será gerado para envio ao CNJ. 1:XML GERADO COM SUCESSO; 2:XML GERADO COM ERRO;
    3:ENVIADO; 4:RECEBIDO NO CNJ; 5:AGUARDANDO PROCESSAMENTO NO CNJ; 6:PROCESSADO COM SUCESSO NO CNJ;
    7:DUPLICADO NO CNJ; 8:PROCESSADO COM ERRO NO CNJ;9:ERRO NO ARQUIVO NO CNJ';

COMMENT ON COLUMN datajud.tb_lote_processo.en_origem_processo
    IS 'Origem do Processo. H: Híbrido; L: Legado; P: PJe';
    
COMMENT ON COLUMN datajud.tb_lote_processo.conteudo_xml
    IS 'Arquivo XML enviado ao CNJ.';

    
alter table datajud.tb_lote_processo
add constraint tb_lote_processo_fk01 foreign key (id_lote)
references datajud.tb_lote (id_lote);

alter table datajud.tb_lote_processo
add constraint tb_lote_processo_fk02 foreign key (id_chave_processo_cnj)
references datajud.tb_chave_processo_cnj (id_chave_processo_cnj);

--------------------------------------------------------------
------------------------ProcessoEnvio-------------------------
--------------------------------------------------------------
-- SEQUENCE: datajud.sq_tb_processo_envio


CREATE SEQUENCE datajud.sq_tb_processo_envio
    INCREMENT 1
    START 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    CACHE 1;
	
-- Table: datajud.tb_processo_envio

CREATE TABLE datajud.tb_processo_envio
(
	id_processo_envio bigint NOT NULL DEFAULT nextval('datajud.sq_tb_processo_envio'::regclass),
    nr_processo varchar(30) NOT NULL,
    en_origem_processo varchar(1) NOT NULL,
    nm_grau varchar(1) NOT NULL,
    id_remessa bigint NOT NULL,
    constraint tb_processo_envio_pk primary key (id_processo_envio),
    constraint tb_processo_envio_uk01 UNIQUE( nr_processo, nm_grau, id_remessa),
    constraint tb_processo_envio_ck01 check (en_origem_processo in ('H','L','P'))
);

COMMENT ON COLUMN datajud.tb_processo_envio.nr_processo
    IS 'Número do Processo.';

    COMMENT ON COLUMN datajud.tb_processo_envio.en_origem_processo
    IS 'Origem do Processo. H: Híbrido; L: Legado; P: PJe';

    COMMENT ON COLUMN datajud.tb_processo_envio.nm_grau
    IS 'Instância do processo.';
    
alter table datajud.tb_processo_envio
add constraint tb_processo_envio_fk01 foreign key (id_remessa)
references datajud.tb_remessa (id_remessa);