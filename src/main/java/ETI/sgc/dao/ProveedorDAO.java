package ETI.sgc.dao;

import ETI.sgc.dto.ProveedorRequest;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;

public class ProveedorDAO {

    private final Jdbi jdbi;

    public ProveedorDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // 🔹 CREAR
    public Long crear(ProveedorRequest req) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                    INSERT INTO proveedores (
                        tipo, nombre, razon_social, nit,
                        telefono, correo, direccion,
                        contacto, descripcion,
                        activo, created_at
                    ) VALUES (
                        :tipo, :nombre, :razon, :nit,
                        :telefono, :correo, :direccion,
                        :contacto, :descripcion,
                        TRUE, NOW()
                    )
                """)
                        .bind("tipo", req.tipo)
                        .bind("nombre", req.nombre)
                        .bind("razon", req.razon_social)
                        .bind("nit", req.nit)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("direccion", req.direccion)
                        .bind("contacto", req.contacto)
                        .bind("descripcion", req.descripcion)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    // 🔹 LISTAR (Limpio para Vue.js)
    public List<Map<String, Object>> listar() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM proveedores ORDER BY id DESC")
                        .mapToMap()
                        .list()
        );
    }

    // 🔹 OBTENER
    public Map<String, Object> obtener(Long id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM proveedores WHERE id = :id")
                        .bind("id", id)
                        .mapToMap()
                        .findOne()
                        .orElse(null)
        );
    }

    // 🔹 ESTADO
    public void cambiarEstado(Long id, boolean activo) {
        jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE proveedores SET activo = :activo WHERE id = :id")
                        .bind("activo", activo)
                        .bind("id", id)
                        .execute()
        );
    }

    // 🔹 ACTUALIZAR
    public void actualizar(Long id, ProveedorRequest req) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                UPDATE proveedores SET
                    tipo = :tipo, nombre = :nombre, razon_social = :razon, nit = :nit,
                    telefono = :telefono, correo = :correo, direccion = :direccion,
                    contacto = :contacto, descripcion = :descripcion
                WHERE id = :id
            """)
                        .bind("tipo", req.tipo)
                        .bind("nombre", req.nombre)
                        .bind("razon", req.razon_social)
                        .bind("nit", req.nit)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("direccion", req.direccion)
                        .bind("contacto", req.contacto)
                        .bind("descripcion", req.descripcion)
                        .bind("id", id)
                        .execute()
        );
    }
}