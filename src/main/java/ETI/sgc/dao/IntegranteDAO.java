package ETI.sgc.dao;

import ETI.sgc.dto.IntegranteRequest;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IntegranteDAO {

    private final Jdbi jdbi;

    public IntegranteDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // 🔹 CREAR
    public Long crear(IntegranteRequest req) {
        return jdbi.withHandle(handle ->
                handle.createUpdate("""
                    INSERT INTO integrantes (
                        codigo, nombre_completo, genero, fecha_nacimiento, edad, lugar_nacimiento,
                        tipo_documento, numero_documento, lugar_expedicion,
                        ciudad_residencia, direccion, telefono, correo,
                        contacto_emergencia_nombre, contacto_emergencia_telefono,
                        nivel_estudio, profesion, situacion_laboral,
                        condicion_salud, descripcion_salud, eps,
                        tipo_miembro, historial_participacion,
                        tiene_pareja, nombre_pareja,
                        disponible_viajar, tiene_pasaporte, tiene_visa_usa,
                        foto_url, activo, created_at
                    ) VALUES (
                        :codigo, :nombre, :genero, :fecha_nacimiento::DATE, :edad, :lugar_nacimiento,
                        :tipo_documento, :numero_documento, :lugar_expedicion,
                        :ciudad_residencia, :direccion, :telefono, :correo,
                        :contacto_nombre, :contacto_tel,
                        :nivel_estudio, :profesion, :situacion_laboral,
                        :condicion_salud, :descripcion_salud, :eps,
                        :tipo_miembro, :historial,
                        :tiene_pareja, :nombre_pareja,
                        :disponible_viajar, :tiene_pasaporte, :tiene_visa,
                        :foto, TRUE, NOW()
                    )
                """)
                        .bind("codigo", UUID.randomUUID().toString())
                        .bind("nombre", req.nombre_completo)
                        .bind("genero", req.genero)
                        .bind("fecha_nacimiento", req.fecha_nacimiento) // Postgres hará el cast con ::DATE
                        .bind("edad", req.edad)
                        .bind("lugar_nacimiento", req.lugar_nacimiento)
                        .bind("tipo_documento", req.tipo_documento)
                        .bind("numero_documento", req.numero_documento)
                        .bind("lugar_expedicion", req.lugar_expedicion)
                        .bind("ciudad_residencia", req.ciudad_residencia)
                        .bind("direccion", req.direccion)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("contacto_nombre", req.contacto_emergencia_nombre)
                        .bind("contacto_tel", req.contacto_emergencia_telefono)
                        .bind("nivel_estudio", req.nivel_estudio)
                        .bind("profesion", req.profesion)
                        .bind("situacion_laboral", req.situacion_laboral)
                        .bind("condicion_salud", req.condicion_salud)
                        .bind("descripcion_salud", req.descripcion_salud)
                        .bind("eps", req.eps)
                        .bind("tipo_miembro", req.tipo_miembro)
                        .bind("historial", req.historial_participacion)
                        .bind("tiene_pareja", req.tiene_pareja)
                        .bind("nombre_pareja", req.nombre_pareja)
                        .bind("disponible_viajar", req.disponible_viajar)
                        .bind("tiene_pasaporte", req.tiene_pasaporte)
                        .bind("tiene_visa", req.tiene_visa_usa)
                        .bind("foto", req.foto_url)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long.class)
                        .one()
        );
    }

    // 🔹 ACTUALIZAR
    public void actualizar(Long id, IntegranteRequest req) {
        jdbi.useHandle(handle ->
                handle.createUpdate("""
                    UPDATE integrantes SET
                        nombre_completo = :nombre, 
                        genero = :genero, 
                        fecha_nacimiento = :fecha_nacimiento::DATE, 
                        edad = :edad, 
                        lugar_nacimiento = :lugar_nacimiento,
                        tipo_documento = :tipo_documento, 
                        numero_documento = :numero_documento, 
                        lugar_expedicion = :lugar_expedicion, 
                        ciudad_residencia = :ciudad_residencia, 
                        direccion = :direccion, 
                        telefono = :telefono, 
                        correo = :correo,
                        contacto_emergencia_nombre = :contacto_nombre, 
                        contacto_emergencia_telefono = :contacto_tel,
                        nivel_estudio = :nivel_estudio, 
                        profesion = :profesion, 
                        situacion_laboral = :situacion_laboral,
                        condicion_salud = :condicion_salud, 
                        descripcion_salud = :descripcion_salud, 
                        eps = :eps, 
                        tipo_miembro = :tipo_miembro, 
                        historial_participacion = :historial,
                        tiene_pareja = :tiene_pareja, 
                        nombre_pareja = :nombre_pareja,
                        disponible_viajar = :disponible_viajar, 
                        tiene_pasaporte = :tiene_pasaporte, 
                        tiene_visa_usa = :tiene_visa, 
                        foto_url = :foto
                    WHERE id = :id
                """)
                        .bind("nombre", req.nombre_completo)
                        .bind("genero", req.genero)
                        .bind("fecha_nacimiento", req.fecha_nacimiento)
                        .bind("edad", req.edad)
                        .bind("lugar_nacimiento", req.lugar_nacimiento)
                        .bind("tipo_documento", req.tipo_documento)
                        .bind("numero_documento", req.numero_documento)
                        .bind("lugar_expedicion", req.lugar_expedicion)
                        .bind("ciudad_residencia", req.ciudad_residencia)
                        .bind("direccion", req.direccion)
                        .bind("telefono", req.telefono)
                        .bind("correo", req.correo)
                        .bind("contacto_nombre", req.contacto_emergencia_nombre)
                        .bind("contacto_tel", req.contacto_emergencia_telefono)
                        .bind("nivel_estudio", req.nivel_estudio)
                        .bind("profesion", req.profesion)
                        .bind("situacion_laboral", req.situacion_laboral)
                        .bind("condicion_salud", req.condicion_salud)
                        .bind("descripcion_salud", req.descripcion_salud)
                        .bind("eps", req.eps)
                        .bind("tipo_miembro", req.tipo_miembro)
                        .bind("historial", req.historial_participacion)
                        .bind("tiene_pareja", req.tiene_pareja)
                        .bind("nombre_pareja", req.nombre_pareja)
                        .bind("disponible_viajar", req.disponible_viajar)
                        .bind("tiene_pasaporte", req.tiene_pasaporte)
                        .bind("tiene_visa", req.tiene_visa_usa)
                        .bind("foto", req.foto_url)
                        .bind("id", id)
                        .execute()
        );
    }

    // 🔹 LISTAR
    public List<Map<String, Object>> listar() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                    SELECT i.*,
                           u.id AS usuario_id,
                           u.username AS usuario_username,
                           u.rol AS usuario_rol,
                           u.activo AS usuario_activo
                    FROM integrantes i
                    LEFT JOIN usuarios u ON u.integrante_id = i.id
                    ORDER BY i.id DESC
                """)
                        .mapToMap()
                        .list()
        );
    }

    // 🔹 LISTAR (Solo integrantes activos)
    public List<Map<String, Object>> listar2() {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                    SELECT i.*,
                           u.id AS usuario_id,
                           u.username AS usuario_username,
                           u.rol AS usuario_rol,
                           u.activo AS usuario_activo
                    FROM integrantes i
                    LEFT JOIN usuarios u ON u.integrante_id = i.id
                    WHERE i.activo = TRUE
                    ORDER BY i.id DESC
                """)
                        .mapToMap()
                        .list()
        );
    }
    public void actualizarFoto(Long id, String rutaFoto) {
        jdbi.useHandle(handle ->
                handle.createUpdate("UPDATE integrantes SET foto_url = :foto WHERE id = :id")
                        .bind("foto", rutaFoto)
                        .bind("id", id)
                        .execute()
        );
    }

    public Object obtener(Long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
            SELECT i.*,
                   u.id AS usuario_id,
                   u.username AS usuario_username,
                   u.rol AS usuario_rol,
                   u.activo AS usuario_activo
            FROM integrantes i
            LEFT JOIN usuarios u ON u.integrante_id = i.id
            WHERE i.id = :id
        """).bind("id", id).mapToMap().findOne().orElse(null));
    }

    public Object obtenerPorCodigo(String codigo) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT * FROM integrantes WHERE codigo = :codigo").bind("codigo", codigo).mapToMap().findOne().orElse(null));
    }

    public void cambiarEstado(Long id, boolean activo) {
        jdbi.useHandle(handle -> handle.createUpdate("UPDATE integrantes SET activo = :activo WHERE id = :id").bind("activo", activo).bind("id", id).execute());
    }
}
