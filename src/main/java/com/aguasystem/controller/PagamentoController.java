package com.aguasystem.controller;

import com.aguasystem.dto.PagamentoDTO;
import com.aguasystem.entity.Pagamento;
import com.aguasystem.service.FaturaService;
import com.aguasystem.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final FaturaService faturaService;

    public PagamentoController(PagamentoService pagamentoService, FaturaService faturaService) {
        this.pagamentoService = pagamentoService;
        this.faturaService = faturaService;
    }

    @GetMapping("/nova/{faturaId}")
    public String novoFormulario(@PathVariable Long faturaId, Model model) {
        var fatura = faturaService.buscarPorId(faturaId);

        PagamentoDTO dto = new PagamentoDTO();
        dto.setFaturaId(faturaId);
        dto.setDataPagamento(LocalDate.now());
        dto.setValorPago(fatura.getSaldoDevedor());

        model.addAttribute("pagamentoDTO", dto);
        model.addAttribute("fatura", fatura);
        model.addAttribute("formasPagamento", Pagamento.FormaPagamento.values());
        return "pagamento/form";
    }

    @PostMapping("/nova")
    public String registar(@Valid @ModelAttribute("pagamentoDTO") PagamentoDTO dto,
                            BindingResult resultado, Model model,
                            RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("fatura", faturaService.buscarPorId(dto.getFaturaId()));
            model.addAttribute("formasPagamento", Pagamento.FormaPagamento.values());
            return "pagamento/form";
        }

        pagamentoService.registarPagamento(dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Pagamento registado com sucesso");
        return "redirect:/faturas/" + dto.getFaturaId();
    }
}
