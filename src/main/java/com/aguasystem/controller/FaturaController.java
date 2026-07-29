package com.aguasystem.controller;

import com.aguasystem.dto.FaturaEdicaoDTO;
import com.aguasystem.entity.Fatura;
import com.aguasystem.service.ExcelService;
import com.aguasystem.service.FaturaService;
import com.aguasystem.service.MensagemService;
import com.aguasystem.service.PdfService;
import com.aguasystem.service.ZumboPayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/faturas")
public class FaturaController {

    private final FaturaService faturaService;
    private final PdfService pdfService;
    private final ExcelService excelService;
    private final ZumboPayService zumboPayService;

    public FaturaController(FaturaService faturaService, PdfService pdfService, ExcelService excelService,
                             ZumboPayService zumboPayService) {
        this.faturaService = faturaService;
        this.pdfService = pdfService;
        this.excelService = excelService;
        this.zumboPayService = zumboPayService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) Fatura.StatusFatura status,
                          @RequestParam(required = false) String mes,
                          Model model) {

        LocalDate mesReferencia = (mes != null && !mes.isBlank())
                ? LocalDate.parse(mes + "-01")
                : LocalDate.now().withDayOfMonth(1);

        model.addAttribute("faturas", faturaService.listarPorMes(mesReferencia, status));
        model.addAttribute("statusSelecionado", status);
        model.addAttribute("mesSelecionado", mesReferencia);
        model.addAttribute("todosStatus", Fatura.StatusFatura.values());
        return "fatura/lista";
    }

    @GetMapping("/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel(@RequestParam(required = false) Fatura.StatusFatura status,
                                                 @RequestParam(required = false) String mes) {
        LocalDate mesReferencia = (mes != null && !mes.isBlank())
                ? LocalDate.parse(mes + "-01")
                : LocalDate.now().withDayOfMonth(1);

        byte[] excel = excelService.gerarExcelFaturas(faturaService.listarPorMes(mesReferencia, status));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=faturas.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        model.addAttribute("fatura", faturaService.buscarPorId(id));
        return "fatura/detalhes";
    }

    @GetMapping("/{id}/editar")
    public String editarFormulario(@PathVariable Long id, Model model) {
        Fatura fatura = faturaService.buscarPorId(id);

        if (fatura.getValorPago().signum() > 0 ||
                (fatura.getStatus() != Fatura.StatusFatura.PENDENTE && fatura.getStatus() != Fatura.StatusFatura.VENCIDA)) {
            model.addAttribute("mensagemErro",
                    "Esta fatura nao pode ser editada (ja tem pagamento registado, ou o status nao permite edicao)");
            return "erro/erro-negocio";
        }

        FaturaEdicaoDTO dto = new FaturaEdicaoDTO();
        dto.setConsumoM3(fatura.getConsumoM3());
        dto.setDataVencimento(fatura.getDataVencimento());

        model.addAttribute("faturaEdicaoDTO", dto);
        model.addAttribute("fatura", fatura);
        return "fatura/editar";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("faturaEdicaoDTO") FaturaEdicaoDTO dto,
                             BindingResult resultado, Model model,
                             RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("fatura", faturaService.buscarPorId(id));
            return "fatura/editar";
        }

        faturaService.atualizarConsumoEVencimento(id, dto.getConsumoM3(), dto.getDataVencimento());
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Fatura corrigida com sucesso");
        return "redirect:/faturas/" + id;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> baixarPdf(@PathVariable Long id) {
        Fatura fatura = faturaService.buscarPorId(id);
        byte[] pdf = pdfService.gerarPdfFaturaUnica(fatura);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=fatura-" + fatura.getNumeroFatura() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Gera um PDF combinando varias faturas selecionadas, 2 por folha
     * (de clientes diferentes), para impressao em lote economizando papel.
     */
    @PostMapping("/pdf-lote")
    public ResponseEntity<byte[]> baixarPdfLote(@RequestParam("faturaIds") List<Long> faturaIds) {
        List<Fatura> faturas = faturaService.buscarPorIds(faturaIds);
        byte[] pdf = pdfService.gerarPdfLote(faturas);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=faturas-lote.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam(required = false) String motivo,
                            RedirectAttributes redirectAttributes) {
        faturaService.cancelar(id, motivo);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Fatura cancelada com sucesso");
        return "redirect:/faturas/" + id;
    }

    @PostMapping("/{id}/reenviar-sms")
    public String reenviarSms(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        MensagemService.ResultadoSms resultado = faturaService.reenviarNotificacao(id);
        if (resultado.sucesso) {
            redirectAttributes.addFlashAttribute("mensagemSucesso", resultado.mensagem);
        } else {
            redirectAttributes.addFlashAttribute("mensagemErro", "SMS nao enviado: " + resultado.mensagem);
        }
        return "redirect:/faturas/" + id;
    }

    @PostMapping("/{id}/cobrar-zumbopay")
    public String cobrarViaZumboPay(@PathVariable Long id, @RequestParam String canal,
                                     RedirectAttributes redirectAttributes) {
        Fatura fatura = faturaService.buscarPorId(id);

        ZumboPayService.Canal canalEnum = "emola".equalsIgnoreCase(canal)
                ? ZumboPayService.Canal.EMOLA
                : ZumboPayService.Canal.MPESA;

        ZumboPayService.ResultadoCobranca resultado = zumboPayService.cobrar(
                canalEnum,
                fatura.getSaldoDevedor(),
                fatura.getCliente().getTelefone(),
                fatura.getCliente().getNomeCompleto(),
                fatura.getNumeroFatura()
        );

        faturaService.registarTentativaCobranca(id, resultado.reference, resultado.status);

        if ("erro".equals(resultado.status) || "declined".equals(resultado.status)) {
            redirectAttributes.addFlashAttribute("mensagemErro", resultado.mensagem);
        } else {
            redirectAttributes.addFlashAttribute("mensagemSucesso", resultado.mensagem);
        }
        return "redirect:/faturas/" + id;
    }
}
