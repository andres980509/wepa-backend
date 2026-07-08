package ETI.sgc.dao;

import org.jdbi.v3.core.Jdbi;

public class ValidacionConceptoDAO {
    private final Jdbi jdbi;

    public ValidacionConceptoDAO(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void crear(Long conceptoId, String tipoTercero) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO validacion_concepto (concepto_id, tipo_tercero, activo)
           
                VALUES (:c, :t::tipo_tercero_enum, true)
                ON CONFLICT (concepto_id, tipo_tercero) DO UPDATE SET activo = true
            """)
                        .bind("c", conceptoId)
                        .bind("t", tipoTercero)
                        .execute()
        );
    }

    public boolean existe(Long conceptoId, String tipoTercero) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) 
                FROM validacion_concepto
                WHERE concepto_id = :c 
                  /* Cast vital para que el operador '=' funcione con el ENUM */
                  AND tipo_tercero = :t::tipo_tercero_enum
                  AND activo = true
            """)
                        .bind("c", conceptoId)
                        .bind("t", tipoTercero)
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }
}