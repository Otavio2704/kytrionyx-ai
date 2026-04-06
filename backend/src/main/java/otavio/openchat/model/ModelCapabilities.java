package otavio.openchat.model;

import lombok.*;

/**
 * DTO com as capabilities detectadas de um modelo Ollama.
 * Usado pelo frontend para habilitar/desabilitar funcionalidades da UI.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelCapabilities {

    /** Nome do modelo */
    private String modelName;

    /** Suporta thinking mode (<think> tags) — QwQ, DeepSeek-R1, Qwen3 */
    private boolean supportsThinking;

    /** Suporta visão / imagens — LLaVA, Qwen2-VL, etc. */
    private boolean supportsVision;

    /** Contexto máximo em tokens (0 = desconhecido) */
    private int contextLength;

    /** Família do modelo (ex: qwen3moe, llama, mistral) */
    private String family;

    /** Tamanho de parâmetros (ex: 480B, 8B) */
    private String parameterSize;

    /**
     * Tipos de arquivo aceitos com base nas capabilities.
     * Calculado a partir de supportsVision.
     */
    public java.util.List<String> getAcceptedFileTypes() {
        java.util.List<String> types = new java.util.ArrayList<>();
        // Todos os modelos aceitam texto extraído de documentos
        types.add("txt");
        types.add("md");
        types.add("pdf");
        types.add("docx");
        // Apenas modelos com visão aceitam imagens diretamente
        if (supportsVision) {
            types.add("jpg");
            types.add("jpeg");
            types.add("png");
            types.add("webp");
            types.add("gif");
        }
        return types;
    }
}