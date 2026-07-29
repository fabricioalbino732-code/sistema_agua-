package com.aguasystem.controller;

import com.aguasystem.service.CorteReligacaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cortes")
public class CorteReligacaoController {

    private final CorteReligacaoService corteReligacaoService;

    public CorteReligacaoController(CorteReligacaoService corteReligacaoService) {
        this.corteReligacaoService = corteReligacaoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("cortes", corteReligacaoService.listarCortadosAtualmente());
        return "corte/lista";
    }

    @PostMapping("/{clienteId}/cortar")
    public String cortar(@PathVariable Long clienteId, @RequestParam String motivo,
                          @RequestParam(required = false) String registadoPor,
                          RedirectAttributes redirectAttributes) {
        corteReligacaoService.registarCorte(clienteId, motivo, registadoPor);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Corte registado com sucesso");
        return "redirect:/clientes/" + clienteId;
    }

    @PostMapping("/{clienteId}/religar")
    public String religar(@PathVariable Long clienteId,
                           @RequestParam(required = false) String registadoPor,
                           RedirectAttributes redirectAttributes) {
        corteReligacaoService.registarReligacao(clienteId, registadoPor);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Religacao registada com sucesso");
        return "redirect:/clientes/" + clienteId;
    }
}
