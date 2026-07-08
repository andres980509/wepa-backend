package ETI.sgc.model;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public class Usuario {

    public Long id;
    public String username;
    public String password_hash;
    public String rol;
    public boolean activo;
    public Long usuario_datos_id;
    public Long integrante_id;

    // Relación
    public UsuarioDatos datos;
}
