package com.aguasystem.controller;

import com.aguasystem.dto.LeituraRemotaDTO;
import com.aguasystem.entity.LeituraContador;
import com.aguasystem.service.ClienteService;
import com.aguasystem.service.FileStorageService;
import com.aguasystem.service.LeituraService;
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
@RequestMapping("/leituras")
public class LeituraRemotaController {

    private final LeituraService leituraService;
    private final ClienteService clienteService;
    private final FileStorageService fileStorageService;

    public LeituraRemotaController(LeituraService leituraService, ClienteService clienteService,
                                    FileStorageService fileStorageService) {
        this.leituraService = leituraService;
        this.clienteService = clienteService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/remota")
    public String formulario(Model model) {
        model.addAttribute("leituraRemotaDTO", new LeituraRemotaDTO());
        model.addAttribute("clientes", clienteService.listarAtivos());
        return "leitura/remota";
    }

    @PostMapping("/remota")
    public String registar(@Valid @ModelAttribute("leituraRemotaDTO") LeituraRemotaDTO dto,
                            BindingResult resultado, Model model,
                            RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("clientes", clienteService.listarAtivos());
            return "leitura/remota";
        }

        LeituraContador leitura = leituraService.registarRemota(dto);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Leitura registada com sucesso para " + leitura.getCliente().getNomeCompleto() +
                ". Consumo: " + leitura.getConsumoM3() + " m3");
        return "redirect:/leituras/remota";
    }

    /**
     * Serve a foto de uma leitura de forma autenticada — nunca fica
     * acessivel publicamente sem login, ao contrario de um ficheiro estatico.
     */
    @GetMapping("/foto/{leituraId}")
    public ResponseEntity<byte[]> verFoto(@PathVariable Long leituraId) {
        LeituraContador leitura = leituraService.buscarPorId(leituraId);
        byte[] foto = fileStorageService.lerFoto(leitura.getFotoContadorPath());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(foto);
    }
}
