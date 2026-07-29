package com.aguasystem.controller;

import com.aguasystem.service.ExcelService;
import com.aguasystem.service.RelatorioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final ExcelService excelService;

    public RelatorioController(RelatorioService relatorioService, ExcelService excelService) {
        this.relatorioService = relatorioService;
        this.excelService = excelService;
    }

    @GetMapping("/dividas")
    public String relatorioDividas(Model model) {
        model.addAttribute("relatorio", relatorioService.obterRelatorioDividas());
        return "relatorio/dividas";
    }

    @GetMapping("/dividas/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel() {
        byte[] excel = excelService.gerarExcelRelatorioDividas(relatorioService.obterRelatorioDividas());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio-dividas.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
