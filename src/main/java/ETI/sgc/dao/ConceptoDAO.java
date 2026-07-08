// ETI.sgc.dao.ConceptoDAO.java
package ETI.sgc.dao;

import ETI.sgc.model.Concepto;
import ETI.sgc.dto.ConceptoRequest;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Map;

public class ConceptoDAO {
    private final Jdbi jdbi;

    public ConceptoDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<Map<String, Object>> listarConValidaciones() {
        return jdbi.withHandle(h ->
                h.createQuery("""
            SELECT c.id, c.nombre, c.descripcion, 
                   string_agg(v.tipo_tercero::text, ',') as tipos 
            FROM conceptos c
            LEFT JOIN validacion_concepto v ON c.id = v.concepto_id
            WHERE c.activo = true
            GROUP BY c.id, c.nombre, c.descripcion
            ORDER BY c.nombre ASC
        """)
                        .mapToMap()
                        .list()
        );
    }

    public Long crearCompleto(ConceptoRequest req) {
        return jdbi.inTransaction(h -> {
            // 1. Insertar Concepto
            Long id = h.createUpdate("""
            INSERT INTO conceptos (nombre, descripcion, activo)
            VALUES (:nombre, :descripcion, true)
        """)
                    .bind("nombre", req.nombre)
                    .bind("descripcion", req.descripcion)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();

            if (req.tipos_terceros != null) {
                for (String tipo : req.tipos_terceros) {
                    h.createUpdate("""
                    INSERT INTO validacion_concepto (concepto_id, tipo_tercero)
                    /* Agregamos el cast explícito al tipo enum de tu base de datos */
                    VALUES (:id, :tipo::tipo_tercero_enum)
                """)
                            .bind("id", id)
                            .bind("tipo", tipo)
                            .execute();
                }
            }
            return id;
        });
    }
}