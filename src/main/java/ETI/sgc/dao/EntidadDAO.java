package ETI.sgc.dao;

import ETI.sgc.dto.EntidadRequest;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;

public class EntidadDAO {

    private final Jdbi jdbi;

    public EntidadDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Long crear(EntidadRequest req) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                    INSERT INTO entidades (
                        nombre, tipo, nit, telefono, correo, descripcion, activo, created_at
                    ) VALUES (
                        :nombre, :tipo, :nit, :telefono, :correo, :descripcion, TRUE, NOW()
                    )
                """)
                        .bind("nombre", req.nombre)
                        .bind("tipo", req.tipo)
                        .bind("nit", req.nit)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("descripcion", req.descripcion)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    public List<Map<String, Object>> listar() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM entidades ORDER BY id DESC")
                        .mapToMap()
                        .list()
        );
    }

    public Map<String, Object> obtener(Long id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM entidades WHERE id = :id")
                        .bind("id", id)
                        .mapToMap()
                        .findOne()
                        .orElse(null)
        );
    }

    public void actualizar(Long id, EntidadRequest req) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE entidades SET
                        nombre = :nombre, tipo = :tipo, nit = :nit,
                        telefono = :telefono, correo = :correo, descripcion = :descripcion
                    WHERE id = :id
                """)
                        .bind("nombre", req.nombre)
                        .bind("tipo", req.tipo)
                        .bind("nit", req.nit)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("descripcion", req.descripcion)
                        .bind("id", id)
                        .execute()
        );
    }

    public void cambiarEstado(Long id, boolean activo) {
        jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE entidades SET activo = :activo WHERE id = :id")
                        .bind("activo", activo)
                        .bind("id", id)
                        .execute()
        );
    }
}