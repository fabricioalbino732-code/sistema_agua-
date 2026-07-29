package com.aguasystem.service;

import com.aguasystem.entity.Usuario;
import com.aguasystem.exception.NegocioException;
import com.aguasystem.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilizador nao encontrado: " + username));

        if (!usuario.getAtivo()) {
            throw new UsernameNotFoundException("Este utilizador esta desativado");
        }

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getSenhaHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new NegocioException("Utilizador nao encontrado"));
    }

    @Transactional
    public Usuario criar(String username, String senha, String nomeCompleto, Usuario.Perfil perfil) {
        if (usuarioRepository.existsByUsername(username)) {
            throw new NegocioException("Ja existe um utilizador com este username");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setNomeCompleto(nomeCompleto);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void inativar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Utilizador nao encontrado"));
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void alterarSenha(String username, String novaSenha) {
        Usuario usuario = buscarPorUsername(username);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
    }

    /**
     * Cria o utilizador administrador padrao na primeira vez que o sistema
     * corre, caso ainda nao exista nenhum utilizador na base de dados.
     * Usado pelo DataSeeder no arranque da aplicacao.
     */
    @Transactional
    public void garantirAdminPadrao() {
        if (usuarioRepository.count() == 0) {
            criar("admin", "admin123", "Administrador", Usuario.Perfil.ADMIN);
        }
    }
}
