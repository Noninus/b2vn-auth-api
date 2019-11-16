package br.com.b2vnauthapi.b2vnauthapi.modules.usuario.service;

import br.com.b2vnauthapi.b2vnauthapi.config.ratelimit.RateLimit;
import br.com.b2vnauthapi.b2vnauthapi.exceptions.validacao.ValidacaoException;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.dto.UsuarioAdminRequest;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.dto.UsuarioAutenticado;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.dto.UsuarioRequest;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.model.Permissao;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.model.Usuario;
import br.com.b2vnauthapi.b2vnauthapi.modules.usuario.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static br.com.b2vnauthapi.b2vnauthapi.modules.usuario.enums.EPermissao.ADMIN;
import static br.com.b2vnauthapi.b2vnauthapi.modules.usuario.exception.UsuarioException.*;
import static br.com.b2vnauthapi.b2vnauthapi.modules.usuario.model.Usuario.of;

@Service
@Slf4j
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public void save(UsuarioRequest usuarioRequest) {
        var usuario = of(usuarioRequest);
        validarDadosUsuario(usuario);
        usuario.setSenha(passwordEncoder.encode(usuarioRequest.getSenha()));
        usuario.setDataCadastro(LocalDateTime.now());
        usuario.setUltimoAcesso(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    private void validarDadosUsuario(Usuario usuario) {
        validarEmailExistente(usuario);
        validarCpfExistente(usuario);
    }

    private void validarEmailExistente(Usuario usuario) {
        usuarioRepository.findByEmail(usuario.getEmail())
            .ifPresent(usuarioExistente -> {
                if (usuario.isNovoCadastro() || !usuario.getId().equals(usuarioExistente.getId())) {
                    throw USUARIO_EMAIL_JA_CADASTRADO.getException();
                }
            });
    }

    private void validarCpfExistente(Usuario usuario) {
        usuarioRepository.findByCpf(usuario.getCpf())
            .ifPresent(usuarioExistente -> {
                if (usuario.isNovoCadastro() || !usuario.getId().equals(usuarioExistente.getId())) {
                    throw USUARIO_CPF_JA_CADASTRADO.getException();
                }
            });
    }

    @Transactional
    @RateLimit(1)
    public UsuarioAutenticado getUsuarioAutenticadoAtualizaUltimaData() {
        var usuarioAtualizado = usuarioRepository
            .findById(getUsuarioAutenticado().getId())
            .orElseThrow(USUARIO_NAO_ENCONTRADO::getException);
        return UsuarioAutenticado.of(atualizarUltimoAcesso(usuarioAtualizado));
    }

    @Transactional
    private Usuario atualizarUltimoAcesso(Usuario usuario) {
        usuario.setUltimoAcesso(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    public UsuarioAutenticado getUsuarioAutenticado() {
        var email = "";
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try {
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            }
        } catch (Exception ex) {
            log.error(ex.getMessage());
            throw USUARIO_SEM_SESSAO.getException();
        }
        return UsuarioAutenticado
            .of(usuarioRepository.findByEmail(email).orElseThrow(USUARIO_NAO_ENCONTRADO::getException));
    }

    public Page<Usuario> getUsuarios(Integer page, Integer size) {
        var pageRequest = PageRequest.of(page, size);
        var usuarioAutenticado = getUsuarioAutenticado();
        if (usuarioAutenticado.isAdmin()) {
            return usuarioRepository.findAll(pageRequest);
        }
        return usuarioRepository.findById(usuarioAutenticado.getId(), pageRequest);
    }

    @Transactional
    public void tornarAdmin(UsuarioAdminRequest request) {
        var usuario = usuarioRepository.findByCpf(request.getCpf())
            .orElseThrow(USUARIO_NAO_ENCONTRADO::getException);
        if (usuario.getPermissao().getCodigo().equals(ADMIN)) {
            usuario.setPermissao(new Permissao(1, ADMIN, "Administrador"));
            usuarioRepository.save(usuario);
        } else {
            throw new ValidacaoException("Esse usuário já é um administrador");
        }
    }
}
