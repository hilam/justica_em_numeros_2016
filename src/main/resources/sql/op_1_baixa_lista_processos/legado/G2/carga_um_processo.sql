SELECT
    nr_processo
FROM
    stage_legado_2grau.processo
WHERE 
	1=1
	and nr_processo = ?
	and cd_orgao_julgador is not null -- foi decidido que serão desconsiderados os processo que não puderam ter o orgao julgador mapeado para serventia por fazer parte da chave
