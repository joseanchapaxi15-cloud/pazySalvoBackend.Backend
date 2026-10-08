package ec.inamhi.pazysalvo.domain;

public enum Area {
    DIRECCION_UNIDAD,
    GESTION_ADMINISTRATIVA,
    TIC,
    GESTION_FINANCIERA,
    SEGURIDAD_INFORMACION,
    RECURSOS_HUMANOS,
    RECEPCION_DOCUMENTOS;

    public static Area fromUnitLabel(String unit) {
        if (unit == null || unit.isBlank()) return null;
        String normalized = java.text.Normalizer.normalize(unit.trim(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("tic") || normalized.contains("tecnolog")) return TIC;
        if (normalized.contains("financ")) return GESTION_FINANCIERA;
        if (normalized.contains("seguridad")) return SEGURIDAD_INFORMACION;
        if (normalized.contains("recursos humanos") || normalized.equals("talento humano")) return RECURSOS_HUMANOS;
        if (normalized.contains("recepcion") || normalized.contains("documentos")) return RECEPCION_DOCUMENTOS;
        if (normalized.contains("administrativ")) return GESTION_ADMINISTRATIVA;
        return null;
    }
}
