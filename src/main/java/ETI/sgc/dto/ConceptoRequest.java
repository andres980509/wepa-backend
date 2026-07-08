package ETI.sgc.dto;

import java.util.List;

public class ConceptoRequest {
    public String nombre;
    public String descripcion;
    public List<String> tipos_terceros; // Aquí enviamos las validaciones de una vez
}