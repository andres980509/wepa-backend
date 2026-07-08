package ETI.sgc.controller;

import ETI.sgc.dao.UsuarioDAO;
import ETI.sgc.dto.LoginRequest;
import ETI.sgc.error.ApiException;
import ETI.sgc.model.Usuario;
import ETI.sgc.security.JwtUtil;
import ETI.sgc.security.Rbac;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;

public class AuthController {

    private final UsuarioDAO usuarioDao;

    public AuthController(UsuarioDAO usuarioDao) {
        this.usuarioDao = usuarioDao;
    }

    public void routes(Javalin app) {
        app.post("/api/login", this::login);
        app.post("/api2/login", this::login); // Compatibilidad con frontend actual.
        app.post("/login", this::login);      // Compatibilidad con pruebas/manual.
    }

    private void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);

        if (req.username == null || req.username.isBlank() || req.password == null || req.password.isBlank()) {
            throw new ApiException(400, "Usuario y contrasena son obligatorios");
        }

        Usuario user = usuarioDao.buscarPorUsername(req.username.trim());

        if (user == null || !BCrypt.checkpw(req.password, user.password_hash)) {
            throw new ApiException(401, "Credenciales invalidas");
        }

        if (!user.activo) {
            throw new ApiException(403, "Usuario inactivo");
        }

        String token = JwtUtil.generar(user);

        ctx.json(Map.of(
                "token", token,
                "rol", user.rol,
                "username", user.username,
                "permisos", Rbac.permissionsForRole(user.rol)
        ));
    }
}
