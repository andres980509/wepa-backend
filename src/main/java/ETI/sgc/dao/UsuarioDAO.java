package ETI.sgc.dao;

import ETI.sgc.model.Usuario;
import ETI.sgc.model.UsuarioDatos;
import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UsuarioDAO {

    private final Jdbi jdbi;

    public UsuarioDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Usuario buscarPorUsername(String username) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                    SELECT id, username, password_hash, rol, activo, usuario_datos_id, integrante_id
                    FROM usuarios
                    WHERE username = :username
                """)
                        .bind("username", username)
                        .map((rs, ctx) -> mapUsuario(rs))
                        .findOne()
                        .orElse(null)
        );
    }

    public Usuario buscarPorId(Long id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                    SELECT id, username, password_hash, rol, activo, usuario_datos_id, integrante_id
                    FROM usuarios
                    WHERE id = :id
                """)
                        .bind("id", id)
                        .map((rs, ctx) -> mapUsuario(rs))
                        .findOne()
                        .orElse(null)
        );
    }

    public Usuario buscarPorIntegranteId(Long integranteId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                    SELECT id, username, password_hash, rol, activo, usuario_datos_id, integrante_id
                    FROM usuarios
                    WHERE integrante_id = :integranteId
                    ORDER BY id DESC
                    LIMIT 1
                """)
                        .bind("integranteId", integranteId)
                        .map((rs, ctx) -> mapUsuario(rs))
                        .findOne()
                        .orElse(null)
        );
    }

    public Long crearUsuarioCompleto(
            String username,
            String passwordHash,
            String rol,
            String cedula,
            String nombres,
            String apellidos,
            String email,
            String telefono,
            String direccion
    ) {
        return crearUsuarioCompleto(username, passwordHash, rol, cedula, nombres, apellidos, email, telefono, direccion, null);
    }

    public Long crearUsuarioCompleto(
            String username,
            String passwordHash,
            String rol,
            String cedula,
            String nombres,
            String apellidos,
            String email,
            String telefono,
            String direccion,
            Long integranteId
    ) {
        return jdbi.inTransaction(handle -> {
            Long datosId = handle.createUpdate("""
                INSERT INTO usuarios_datos
                (cedula, nombres, apellidos, email, telefono, direccion, created_at)
                VALUES
                (:cedula, :nombres, :apellidos, :email, :telefono, :direccion, NOW())
            """)
                    .bind("cedula", cedula)
                    .bind("nombres", nombres)
                    .bind("apellidos", apellidos)
                    .bind("email", email)
                    .bind("telefono", telefono)
                    .bind("direccion", direccion)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            return handle.createUpdate("""
                INSERT INTO usuarios
                (username, password_hash, rol, activo, usuario_datos_id, integrante_id)
                VALUES
                (:username, :password_hash, :rol, TRUE, :datosId, :integranteId)
            """)
                    .bind("username", username)
                    .bind("password_hash", passwordHash)
                    .bind("rol", rol)
                    .bind("datosId", datosId)
                    .bind("integranteId", integranteId)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();
        });
    }

    public void asociarIntegrante(Long usuarioId, Long integranteId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE usuarios
                    SET integrante_id = :integranteId
                    WHERE id = :id
                """)
                        .bind("integranteId", integranteId)
                        .bind("id", usuarioId)
                        .execute()
        );
    }

    public void desasociarIntegrante(Long integranteId) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE usuarios
                    SET integrante_id = NULL
                    WHERE integrante_id = :integranteId
                """)
                        .bind("integranteId", integranteId)
                        .execute()
        );
    }

    public void cambiarEstado(Long usuarioId, boolean activo) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE usuarios
                    SET activo = :activo
                    WHERE id = :id
                """)
                        .bind("activo", activo)
                        .bind("id", usuarioId)
                        .execute()
        );
    }

    public void resetPassword(Long usuarioId, String passwordHash) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE usuarios
                    SET password_hash = :hash
                    WHERE id = :id
                """)
                        .bind("hash", passwordHash)
                        .bind("id", usuarioId)
                        .execute()
        );
    }

    public List<Usuario> listarUsuarios() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                SELECT
                    u.id u_id,
                    u.username u_username,
                    u.rol u_rol,
                    u.activo u_activo,
                    u.integrante_id u_integrante_id,
                    d.id d_id,
                    d.cedula d_cedula,
                    d.nombres d_nombres,
                    d.apellidos d_apellidos,
                    d.email d_email,
                    d.telefono d_telefono,
                    d.direccion d_direccion
                FROM usuarios u
                JOIN usuarios_datos d ON d.id = u.usuario_datos_id
                ORDER BY u.id DESC
            """)
                        .map((rs, ctx) -> {
                            Usuario u = new Usuario();
                            u.id = rs.getLong("u_id");
                            u.username = rs.getString("u_username");
                            u.rol = rs.getString("u_rol");
                            u.activo = rs.getBoolean("u_activo");
                            u.integrante_id = nullableLong(rs, "u_integrante_id");

                            UsuarioDatos d = new UsuarioDatos();
                            d.id = rs.getLong("d_id");
                            d.cedula = rs.getString("d_cedula");
                            d.nombres = rs.getString("d_nombres");
                            d.apellidos = rs.getString("d_apellidos");
                            d.email = rs.getString("d_email");
                            d.telefono = rs.getString("d_telefono");
                            d.direccion = rs.getString("d_direccion");

                            u.datos = d;
                            return u;
                        })
                        .list()
        );
    }

    private Usuario mapUsuario(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.password_hash = rs.getString("password_hash");
        u.rol = rs.getString("rol");
        u.activo = rs.getBoolean("activo");
        u.usuario_datos_id = nullableLong(rs, "usuario_datos_id");
        u.integrante_id = nullableLong(rs, "integrante_id");
        return u;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
