package com.aguasystem.controller;

import com.aguasystem.dto.LeituraDTO;
import com.aguasystem.entity.LeituraContador;
import com.aguasystem.service.ClienteService;
import com.aguasystem.service.FaturaService;
import com.aguasystem.service.LeituraService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/leituras")
public class LeituraController {

    private final LeituraService leituraService;
    private final ClienteService clienteService;
    private final FaturaService faturaService;

    public LeituraController(LeituraService leituraService, ClienteService clienteService,
                              FaturaService faturaService) {
        this.leituraService = leituraService;
        this.clienteService = clienteService;
        this.faturaService = faturaService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String mes, Model model) {
        LocalDate mesReferencia = (mes != null && !mes.isBlank())
                ? LocalDate.parse(mes + "-01")
                : LocalDate.now().withDayOfMonth(1);

        model.addAttribute("leituras", leituraService.listarPorMes(mesReferencia));
        model.addAttribute("mesSelecionado", mesReferencia);
        return "leitura/lista";
    }

    @GetMapping("/nova")
    public String novoFormulario(Model model) {
        LeituraDTO dto = new LeituraDTO();
        dto.setDataLeitura(LocalDate.now());
        dto.setMesReferencia(LocalDate.now().withDayOfMonth(1));
        model.addAttribute("leituraDTO", dto);
        model.addAttribute("clientes", clienteService.listarAtivos());
        return "leitura/form";
    }

    @PostMapping("/nova")
    public String registar(@Valid @ModelAttribute("leituraDTO") LeituraDTO dto,
                            BindingResult resultado, Model model,
                            RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("clientes", clienteService.listarAtivos());
            return "leitura/form";
        }

        LeituraContador leitura = leituraService.registar(dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Leitura registada com sucesso. Consumo: " + leitura.getConsumoM3() + " m3");
        return "redirect:/leituras";
    }

    /** Endpoint AJAX para sugerir a leitura anterior automaticamente. */
    @GetMapping("/sugerir-anterior")
    @ResponseBody
    public String sugerirAnterior(@RequestParam Long clienteId, @RequestParam String mes) {
        LocalDate mesReferencia = LocalDate.parse(mes + "-01");
        return leituraService.sugerirLeituraAnterior(clienteId, mesReferencia).toString();
    }

    @GetMapping("/{id}/editar")
    public String editarFormulario(@PathVariable Long id, Model model) {
        LeituraContador leitura = leituraService.buscarPorId(id);

        LeituraDTO dto = new LeituraDTO();
        dto.setClienteId(leitura.getCliente().getId());
        dto.setMesReferencia(leitura.getMesReferencia());
        dto.setDataLeitura(leitura.getDataLeitura());
        dto.setLeituraAnterior(leitura.getLeituraAnterior());
        dto.setLeituraAtual(leitura.getLeituraAtual());
        dto.setResponsavelLeitura(leitura.getResponsavelLeitura());
        dto.setObservacoes(leitura.getObservacoes());

        model.addAttribute("leituraDTO", dto);
        model.addAttribute("leituraId", id);
        model.addAttribute("clienteNome", leitura.getCliente().getNomeCompleto());
        return "leitura/editar";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("leituraDTO") LeituraDTO dto,
                             BindingResult resultado, Model model,
                             RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("leituraId", id);
            return "leitura/editar";
        }

        leituraService.atualizar(id, dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Leitura corrigida com sucesso");
        return "redirect:/leituras";
    }

    @PostMapping("/{id}/gerar-fatura")
    public String gerarFatura(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LeituraContador leitura = leituraService.buscarPorId(id);
        var resultado = faturaService.gerarFaturaDeLeitura(leitura);

        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Fatura " + resultado.fatura.getNumeroFatura() + " gerada com sucesso. Revê os valores e, "
                + "quando estiver tudo certo, clica em \"Enviar SMS\" para notificar o cliente com o link de "
                + "pagamento.");
        return "redirect:/faturas/" + resultado.fatura.getId();
    }
}
