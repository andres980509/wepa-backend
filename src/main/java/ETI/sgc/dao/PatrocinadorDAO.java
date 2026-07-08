package ETI.sgc.dao;

import ETI.sgc.dto.PatrocinadorRequest;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;

public class PatrocinadorDAO {

    private final Jdbi jdbi;

    public PatrocinadorDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // 🔹 CREAR
    public Long crear(PatrocinadorRequest req) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                    INSERT INTO patrocinadores (
                        nombre, tipo, empresa,
                        telefono, correo,
                        descripcion,
                        activo, created_at
                    ) VALUES (
                        :nombre, :tipo, :empresa,
                        :telefono, :correo,
                        :descripcion,
                        TRUE, NOW()
                    )
                """)
                        .bind("nombre", req.nombre)
                        .bind("tipo", req.tipo)
                        .bind("empresa", req.empresa)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("descripcion", req.descripcion)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    // 🔹 LISTAR - Ahora devuelve una lista limpia
    public List<Map<String, Object>> listar() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM patrocinadores ORDER BY id DESC")
                        .mapToMap()
                        .list()
        );
    }

    // 🔹 OBTENER
    public Map<String, Object> obtener(Long id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM patrocinadores WHERE id = :id")
                        .bind("id", id)
                        .mapToMap()
                        .findOne()
                        .orElse(null)
        );
    }

    // 🔹 ACTUALIZAR
    public void actualizar(Long id, PatrocinadorRequest req) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE patrocinadores SET
                        nombre = :nombre,
                        tipo = :tipo,
                        empresa = :empresa,
                        telefono = :telefono,
                        correo = :correo,
                        descripcion = :descripcion
                    WHERE id = :id
                """)
                        .bind("nombre", req.nombre)
                        .bind("tipo", req.tipo)
                        .bind("empresa", req.empresa)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("descripcion", req.descripcion)
                        .bind("id", id)
                        .execute()
        );
    }

    // 🔹 ESTADO
    public void cambiarEstado(Long id, boolean activo) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE patrocinadores
                    SET activo = :activo
                    WHERE id = :id
                """)
                        .bind("activo", activo)
                        .bind("id", id)
                        .execute()
        );
    }
}