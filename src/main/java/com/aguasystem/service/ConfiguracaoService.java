package com.aguasystem.service;

import com.aguasystem.dto.ConfiguracaoDTO;
import com.aguasystem.entity.Configuracao;
import com.aguasystem.repository.ConfiguracaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsavel pela configuracao global do sistema (singleton).
 *
 * HISTORICO DO BUG CORRIGIDO AQUI:
 * Na versao anterior, o metodo de salvar configuracao fazia
 * "repository.save(new Configuracao(...))" a partir dos dados do formulario,
 * o que criava uma ENTIDADE NOVA (sem ID, ou com ID nulo) a cada save.
 * O JPA interpretava isso como um INSERT, gerando multiplas linhas na tabela.
 * Como a tela de configuracao sempre lia "a primeira linha" (findAll().get(0)
 * ou similar), o sistema podia acabar lendo uma linha antiga e desatualizada
 * apos reiniciar — dando a impressao de que os dados "sumiam".
 *
 * A CORRECAO tem 3 partes:
 * 1. Buscar SEMPRE a entidade existente pelo ID fixo (Configuracao.ID_CONFIGURACAO)
 *    antes de qualquer alteracao — nunca instanciar "new Configuracao()" para salvar.
 * 2. Atualizar os campos da entidade JA GERENCIADA pelo JPA (dentro de uma
 *    transacao), garantindo que o Hibernate gera um UPDATE e nao um INSERT.
 * 3. Se a configuracao ainda nao existir (primeira execucao do sistema),
 *    criar UMA UNICA vez com o ID fixo.
 */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoRepository configuracaoRepository;

    public ConfiguracaoService(ConfiguracaoRepository configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    /**
     * Retorna a configuracao atual do sistema. Se ainda nao existir
     * (primeiro arranque), cria uma configuracao padrao com o ID fixo.
     */
    @Transactional
    public Configuracao obterConfiguracao() {
        return configuracaoRepository.findById(Configuracao.ID_CONFIGURACAO)
                .orElseGet(this::criarConfiguracaoPadrao);
    }

    private Configuracao criarConfiguracaoPadrao() {
        Configuracao configuracao = new Configuracao();
        configuracao.setId(Configuracao.ID_CONFIGURACAO);
        configuracao.setNomeEmpresa("Minha Empresa de Agua");
        configuracao.setPrecoM3(java.math.BigDecimal.valueOf(50));
        configuracao.setDiaVencimento(10);
        return configuracaoRepository.save(configuracao);
    }

    /**
     * Atualiza a configuracao existente. NUNCA instancia uma nova entidade
     * para salvar — sempre busca a entidade gerenciada e atualiza os campos,
     * garantindo um UPDATE em vez de um INSERT.
     */
    @Transactional
    public Configuracao atualizarConfiguracao(ConfiguracaoDTO dto) {
        Configuracao configuracao = obterConfiguracao(); // entidade gerenciada pelo JPA

        configuracao.setNomeEmpresa(dto.getNomeEmpresa());
        configuracao.setNuit(dto.getNuit());
        configuracao.setEndereco(dto.getEndereco());
        configuracao.setTelefone(dto.getTelefone());
        configuracao.setEmail(dto.getEmail());
        configuracao.setPrecoM3(dto.getPrecoM3());
        configuracao.setTaxaFixa(dto.getTaxaFixa() != null ? dto.getTaxaFixa() : java.math.BigDecimal.ZERO);
        configuracao.setDiaVencimento(dto.getDiaVencimento());
        configuracao.setMultaAtrasoPercentual(
                dto.getMultaAtrasoPercentual() != null ? dto.getMultaAtrasoPercentual() : java.math.BigDecimal.ZERO);
        configuracao.setConsumoMinimoM3(
                dto.getConsumoMinimoM3() != null ? dto.getConsumoMinimoM3() : java.math.BigDecimal.ZERO);

        // Como 'configuracao' e uma entidade gerenciada (veio do findById dentro
        // desta mesma transacao), o Hibernate detecta as mudancas automaticamente
        // (dirty checking) e gera um UPDATE ao final da transacao.
        // O save() aqui e explicito por clareza, mas seria feito de qualquer forma.
        return configuracaoRepository.save(configuracao);
    }
}
