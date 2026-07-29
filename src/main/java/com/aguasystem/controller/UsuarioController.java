package com.aguasystem.controller;

import com.aguasystem.dto.UsuarioDTO;
import com.aguasystem.entity.Usuario;
import com.aguasystem.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        return "usuario/lista";
    }

    @GetMapping("/novo")
    public String novoFormulario(Model model) {
        model.addAttribute("usuarioDTO", new UsuarioDTO());
        model.addAttribute("perfis", Usuario.Perfil.values());
        return "usuario/form";
    }

    @PostMapping("/novo")
    public String criar(@Valid @ModelAttribute("usuarioDTO") UsuarioDTO dto,
                         BindingResult resultado, Model model,
                         RedirectAttributes redirectAttributes) {

        if (resultado.hasErrors()) {
            model.addAttribute("perfis", Usuario.Perfil.values());
            return "usuario/form";
        }

        usuarioService.criar(dto.getUsername(), dto.getSenha(), dto.getNomeCompleto(), dto.getPerfil());
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Utilizador criado com sucesso");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        usuarioService.inativar(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Utilizador inativado com sucesso");
        return "redirect:/usuarios";
    }
}
