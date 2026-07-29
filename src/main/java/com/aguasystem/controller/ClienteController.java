package com.aguasystem.controller;

import com.aguasystem.dto.ClienteDTO;
import com.aguasystem.entity.Cliente;
import com.aguasystem.service.ClienteService;
import com.aguasystem.service.ExcelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ExcelService excelService;

    public ClienteController(ClienteService clienteService, ExcelService excelService) {
        this.clienteService = clienteService;
        this.excelService = excelService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String termo, Model model) {
        model.addAttribute("clientes", clienteService.buscar(termo));
        model.addAttribute("termo", termo);
        return "cliente/lista";
    }

    @GetMapping("/exportar-excel")
    public ResponseEntity<byte[]> exportarExcel(@RequestParam(required = false) String termo) {
        byte[] excel = excelService.gerarExcelClientes(clienteService.buscar(termo));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clientes.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/novo")
    public String novoFormulario(Model model) {
        model.addAttribute("clienteDTO", new ClienteDTO());
        model.addAttribute("tiposCliente", Cliente.TipoCliente.values());
        return "cliente/form";
    }

    @PostMapping("/novo")
    public String criar(@Valid @ModelAttribute("clienteDTO") ClienteDTO dto,
                         BindingResult resultado, Model model,
                         RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("tiposCliente", Cliente.TipoCliente.values());
            return "cliente/form";
        }

        Cliente cliente = clienteService.criar(dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Cliente " + cliente.getNomeCompleto() + " registado com sucesso");
        return "redirect:/clientes";
    }

    @GetMapping("/{id}/editar")
    public String editarFormulario(@PathVariable Long id, Model model) {
        Cliente cliente = clienteService.buscarPorId(id);
        model.addAttribute("clienteDTO", converterParaDto(cliente));
        model.addAttribute("clienteId", id);
        model.addAttribute("tiposCliente", Cliente.TipoCliente.values());
        return "cliente/form";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                             @Valid @ModelAttribute("clienteDTO") ClienteDTO dto,
                             BindingResult resultado, Model model,
                             RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("clienteId", id);
            model.addAttribute("tiposCliente", Cliente.TipoCliente.values());
            return "cliente/form";
        }

        clienteService.atualizar(id, dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Cliente atualizado com sucesso");
        return "redirect:/clientes";
    }

    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        model.addAttribute("cliente", clienteService.buscarPorId(id));
        return "cliente/detalhes";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        clienteService.inativar(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Cliente inativado com sucesso");
        return "redirect:/clientes";
    }

    private ClienteDTO converterParaDto(Cliente cliente) {
        ClienteDTO dto = new ClienteDTO();
        dto.setId(cliente.getId());
        dto.setNomeCompleto(cliente.getNomeCompleto());
        dto.setNumeroContador(cliente.getNumeroContador());
        dto.setBairro(cliente.getBairro());
        dto.setEndereco(cliente.getEndereco());
        dto.setTelefone(cliente.getTelefone());
        dto.setEmail(cliente.getEmail());
        dto.setTipoCliente(cliente.getTipoCliente());
        dto.setAtivo(cliente.getAtivo());
        dto.setDataLigacao(cliente.getDataLigacao());
        dto.setObservacoes(cliente.getObservacoes());
        return dto;
    }
}
