package com.aguasystem.controller;

import com.aguasystem.dto.ConfiguracaoDTO;
import com.aguasystem.entity.Configuracao;
import com.aguasystem.service.ConfiguracaoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ConfiguracaoController {

    private final ConfiguracaoService configuracaoService;

    public ConfiguracaoController(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping("/configuracao")
    public String exibirFormulario(Model model) {
        Configuracao config = configuracaoService.obterConfiguracao();
        model.addAttribute("configuracaoDTO", converterParaDto(config));
        return "configuracao/form";
    }

    @PostMapping("/configuracao")
    public String salvar(@Valid @ModelAttribute("configuracaoDTO") ConfiguracaoDTO dto,
                          BindingResult resultado,
                          RedirectAttributes redirectAttributes,
                          Model model) {

        if (resultado.hasErrors()) {
            return "configuracao/form";
        }

        configuracaoService.atualizarConfiguracao(dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Configuracao guardada com sucesso");
        return "redirect:/configuracao";
    }

    private ConfiguracaoDTO converterParaDto(Configuracao config) {
        ConfiguracaoDTO dto = new ConfiguracaoDTO();
        dto.setNomeEmpresa(config.getNomeEmpresa());
        dto.setNuit(config.getNuit());
        dto.setEndereco(config.getEndereco());
        dto.setTelefone(config.getTelefone());
        dto.setEmail(config.getEmail());
        dto.setPrecoM3(config.getPrecoM3());
        dto.setTaxaFixa(config.getTaxaFixa());
        dto.setDiaVencimento(config.getDiaVencimento());
        dto.setMultaAtrasoPercentual(config.getMultaAtrasoPercentual());
        dto.setConsumoMinimoM3(config.getConsumoMinimoM3());
        return dto;
    }
}
