package com.aguasystem.controller;

import com.aguasystem.service.DashboardService;
import com.aguasystem.service.FaturaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final FaturaService faturaService;

    public DashboardController(DashboardService dashboardService, FaturaService faturaService) {
        this.dashboardService = dashboardService;
        this.faturaService = faturaService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.obterEstatisticas());
        return "dashboard/index";
    }

    @PostMapping("/dashboard/atualizar-vencidas")
    public String atualizarVencidas(Model model) {
        int atualizadas = faturaService.atualizarFaturasVencidas();
        model.addAttribute("mensagemSucesso", atualizadas + " fatura(s) marcada(s) como vencida(s)");
        model.addAttribute("stats", dashboardService.obterEstatisticas());
        return "dashboard/index";
    }
}
