package com.aguasystem.dto;

import com.aguasystem.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UsuarioDTO {

    @NotBlank(message = "O username e obrigatorio")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "A senha e obrigatoria")
    @Size(min = 6, message = "A senha deve ter pelo menos 6 caracteres")
    private String senha;

    @NotBlank(message = "O nome completo e obrigatorio")
    @Size(max = 150)
    private String nomeCompleto;

    @NotNull(message = "O perfil e obrigatorio")
    private Usuario.Perfil perfil;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }

    public Usuario.Perfil getPerfil() { return perfil; }
    public void setPerfil(Usuario.Perfil perfil) { this.perfil = perfil; }
}
